package com.hcare.security;

import com.hcare.config.PortalProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long expirationMs;
    private final int portalExpirationDays;

    // C1 fix: portalExpirationDays is now read from PortalProperties (bound to hcare.portal),
    // not from JwtProperties (bound to hcare.jwt). The two prefixes are distinct — Spring Boot
    // would never bind hcare.portal.jwt.expiration-days via hcare.jwt.*.
    public JwtTokenProvider(JwtProperties props, PortalProperties portalProps) {
        this.signingKey = Keys.hmacShaKeyFor(
            props.getSecret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = props.getExpirationMs();
        this.portalExpirationDays = portalProps.getJwt().getExpirationDays();
    }

    /** Generates an admin/scheduler JWT (existing method — unchanged). */
    public String generateToken(UUID userId, UUID agencyId, String role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .subject(userId.toString())
            .claim("agencyId", agencyId.toString())
            .claim("role", role)
            .issuedAt(new Date(now))
            .expiration(new Date(now + expirationMs))
            .signWith(signingKey)
            .compact();
    }

    /**
     * Generates a FAMILY_PORTAL JWT. Subject is fpUserId (the FamilyPortalUser.id).
     * Expiry is governed by portal.jwt.expiration-days (default 30), independent of
     * the admin jwt.expiration-ms.
     */
    public String generateFamilyPortalToken(UUID fpUserId, UUID clientId, UUID agencyId) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(fpUserId.toString())
            .claim("agencyId", agencyId.toString())
            .claim("clientId", clientId.toString())
            .claim("role", "FAMILY_PORTAL")
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(portalExpirationDays, ChronoUnit.DAYS)))
            .signWith(signingKey)
            .compact();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public UUID getUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public UUID getAgencyId(String token) {
        return UUID.fromString(parseClaims(token).get("agencyId", String.class));
    }

    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /**
     * Reads the clientId claim. Only present on FAMILY_PORTAL tokens.
     *
     * NOTE (C2): JwtAuthenticationFilter no longer calls this method — it reads clientId
     * directly from the already-parsed Claims object to avoid a redundant second HMAC parse.
     * If no other caller in this plan invokes getClientId, this method can be removed from
     * JwtTokenProvider to keep the public API minimal. Retain only if a future task requires it.
     */
    public String getClientId(String token) {
        return parseClaims(token).get("clientId", String.class);
    }

    public Claims parseAndValidate(String token) {
        try {
            return parseClaims(token);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
