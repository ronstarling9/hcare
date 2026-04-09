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
    private final SecretKey portalSigningKey;
    private final long expirationMs;
    private final int portalExpirationDays;

    // C1 fix: portalExpirationDays is now read from PortalProperties (bound to hcare.portal),
    // not from JwtProperties (bound to hcare.jwt). The two prefixes are distinct — Spring Boot
    // would never bind hcare.portal.jwt.expiration-days via hcare.jwt.*.
    // H1 fix: portalSigningKey is derived from hcare.portal.jwt.secret, separate from the
    // admin signingKey. Portal tokens are signed and verified with the portal key exclusively,
    // so a compromise of one key does not affect the other population.
    public JwtTokenProvider(JwtProperties props, PortalProperties portalProps) {
        this.signingKey = Keys.hmacShaKeyFor(
            props.getSecret().getBytes(StandardCharsets.UTF_8));
        this.portalSigningKey = Keys.hmacShaKeyFor(
            portalProps.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = props.getExpirationMs();
        this.portalExpirationDays = portalProps.getJwt().getExpirationDays();
        // Fail fast if the two secrets are the same — using identical keys defeats the
        // key-separation guarantee introduced by H1 (separate portal JWT signing key).
        if (this.signingKey.equals(this.portalSigningKey)) {
            throw new IllegalStateException(
                "hcare.portal.jwt.secret must differ from hcare.jwt.secret — " +
                "using the same key defeats portal key separation");
        }
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
     * H1 fix: signed with portalSigningKey (hcare.portal.jwt.secret), not the admin signingKey.
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
            .signWith(portalSigningKey)
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

    public Claims parseAndValidate(String token) {
        try {
            return parseClaims(token);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses and verifies a JWT. Tries the admin signing key first; if that fails, falls
     * back to the portal signing key. This handles both FAMILY_PORTAL tokens (signed with
     * portalSigningKey) and admin/scheduler tokens (signed with signingKey) without needing
     * to peek at unverified claims before signature validation.
     */
    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (io.jsonwebtoken.security.SignatureException e) {
            // Admin key signature mismatch — try portal key. Only signature failures fall
            // back; expired or malformed tokens propagate immediately so callers fail fast.
            return Jwts.parser()
                .verifyWith(portalSigningKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        }
    }
}
