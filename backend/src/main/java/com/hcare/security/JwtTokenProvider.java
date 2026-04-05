package com.hcare.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtTokenProvider(JwtProperties props) {
        this.signingKey = Keys.hmacShaKeyFor(
            props.getSecret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = props.getExpirationMs();
    }

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
     * Parses and validates the token in a single HMAC verification.
     * Returns the Claims payload, or null if the token is invalid or expired.
     * Use this instead of calling validateToken + getXxx separately.
     */
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
