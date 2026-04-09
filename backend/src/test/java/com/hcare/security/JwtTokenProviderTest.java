package com.hcare.security;

import com.hcare.config.PortalProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;
    private PortalProperties portalProps;
    private static final String SECRET =
        "test-secret-key-must-be-at-least-256-bits-long-for-hmac-sha256-algorithm";
    private static final long EXPIRY_MS = 3600_000L;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(SECRET);
        props.setExpirationMs(EXPIRY_MS);
        // C1 fix: inject PortalProperties (bound to hcare.portal) rather than setting
        // portalExpirationDays on JwtProperties, which is bound to hcare.jwt and would
        // never receive the hcare.portal.jwt.expiration-days value from application.yml.
        portalProps = new PortalProperties();
        portalProps.getJwt().setExpirationDays(30);
        provider = new JwtTokenProvider(props, portalProps);
    }

    // ── Existing tests (constructor updated to two-arg form) ───────────────────

    @Test
    void generateToken_returnsNonBlankString() {
        UUID userId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();
        String token = provider.generateToken(userId, agencyId, "ADMIN");
        assertThat(token).isNotBlank();
    }

    @Test
    void validateToken_returnsTrueForValidToken() {
        String token = provider.generateToken(UUID.randomUUID(), UUID.randomUUID(), "SCHEDULER");
        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_returnsFalseForTamperedToken() {
        String token = provider.generateToken(UUID.randomUUID(), UUID.randomUUID(), "ADMIN");
        String tampered = token.substring(0, token.length() - 4) + "XXXX";
        assertThat(provider.validateToken(tampered)).isFalse();
    }

    @Test
    void validateToken_returnsFalseForExpiredToken() throws InterruptedException {
        JwtProperties shortProps = new JwtProperties();
        shortProps.setSecret(SECRET);
        shortProps.setExpirationMs(1L);
        // C6 fix: updated to two-arg constructor — original single-arg call would fail to
        // compile after Task 5 Step 4 changes the JwtTokenProvider constructor signature.
        JwtTokenProvider shortProvider = new JwtTokenProvider(shortProps, portalProps);
        String token = shortProvider.generateToken(UUID.randomUUID(), UUID.randomUUID(), "ADMIN");
        Thread.sleep(10);
        assertThat(shortProvider.validateToken(token)).isFalse();
    }

    @Test
    void getUserIdFromToken_returnsCorrectUUID() {
        UUID userId = UUID.randomUUID();
        String token = provider.generateToken(userId, UUID.randomUUID(), "ADMIN");
        assertThat(provider.getUserId(token)).isEqualTo(userId);
    }

    @Test
    void getAgencyIdFromToken_returnsCorrectUUID() {
        UUID agencyId = UUID.randomUUID();
        String token = provider.generateToken(UUID.randomUUID(), agencyId, "SCHEDULER");
        assertThat(provider.getAgencyId(token)).isEqualTo(agencyId);
    }

    @Test
    void getRoleFromToken_returnsCorrectRole() {
        String token = provider.generateToken(UUID.randomUUID(), UUID.randomUUID(), "CAREGIVER");
        assertThat(provider.getRole(token)).isEqualTo("CAREGIVER");
    }

    @Test
    void parseAndValidate_returns_claims_for_valid_token() {
        UUID userId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();
        String token = provider.generateToken(userId, agencyId, "ADMIN");
        Claims claims = provider.parseAndValidate(token);
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("agencyId", String.class)).isEqualTo(agencyId.toString());
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    void parseAndValidate_returns_null_for_invalid_token() {
        Claims claims = provider.parseAndValidate("not.a.valid.token");
        assertThat(claims).isNull();
    }

    // ── New portal token tests ─────────────────────────────────────────────────

    @Test
    void generateFamilyPortalToken_claimsContainRoleAndClientId() {
        UUID fpuId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();

        String token = provider.generateFamilyPortalToken(fpuId, clientId, agencyId);

        Claims claims = provider.parseAndValidate(token);
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo(fpuId.toString());
        assertThat(claims.get("role", String.class)).isEqualTo("FAMILY_PORTAL");
        assertThat(claims.get("clientId", String.class)).isEqualTo(clientId.toString());
        assertThat(claims.get("agencyId", String.class)).isEqualTo(agencyId.toString());
    }

    @Test
    void getClientId_extractsClientIdClaim() {
        UUID fpuId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();

        String token = provider.generateFamilyPortalToken(fpuId, clientId, agencyId);

        assertThat(provider.getClientId(token)).isEqualTo(clientId.toString());
    }

    @Test
    void adminToken_doesNotHaveClientIdClaim() {
        UUID userId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();
        String token = provider.generateToken(userId, agencyId, "ADMIN");
        Claims claims = provider.parseAndValidate(token);
        assertThat(claims.get("clientId", String.class)).isNull();
    }
}
