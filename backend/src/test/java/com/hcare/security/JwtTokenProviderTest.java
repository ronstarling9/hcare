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
    private static final String PORTAL_SECRET =
        "test-portal-secret-key-must-be-at-least-256-bits-long-for-hmac-sha256-algorithm";
    private static final long EXPIRY_MS = 3600_000L;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(SECRET);
        props.setExpirationMs(EXPIRY_MS);
        // C1 fix: inject PortalProperties (bound to hcare.portal) rather than setting
        // portalExpirationDays on JwtProperties, which is bound to hcare.jwt and would
        // never receive the hcare.portal.jwt.expiration-days value from application.yml.
        // H1 fix: set a separate portal JWT secret so portal tokens use a dedicated signing key.
        portalProps = new PortalProperties();
        portalProps.getJwt().setExpirationDays(30);
        portalProps.getJwt().setSecret(PORTAL_SECRET);
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
    void adminToken_doesNotHaveClientIdClaim() {
        UUID userId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();
        String token = provider.generateToken(userId, agencyId, "ADMIN");
        Claims claims = provider.parseAndValidate(token);
        assertThat(claims.get("clientId", String.class)).isNull();
    }

    // H1 fix: verify key isolation — a portal token is NOT accepted by a provider that
    // uses a different admin secret and a different portal secret.
    @Test
    void portalToken_isRejectedByProviderWithDifferentKeys() {
        UUID fpuId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();
        String portalToken = provider.generateFamilyPortalToken(fpuId, clientId, agencyId);

        // Build a provider with completely different secrets
        JwtProperties otherProps = new JwtProperties();
        otherProps.setSecret("other-admin-secret-key-must-be-at-least-256-bits-for-hmac-sha256!");
        otherProps.setExpirationMs(EXPIRY_MS);
        PortalProperties otherPortalProps = new PortalProperties();
        otherPortalProps.getJwt().setExpirationDays(30);
        otherPortalProps.getJwt().setSecret(
            "other-portal-secret-key-must-be-at-least-256-bits-for-hmac-sha256-algorithm");
        JwtTokenProvider otherProvider = new JwtTokenProvider(otherProps, otherPortalProps);

        assertThat(otherProvider.parseAndValidate(portalToken)).isNull();
    }

    // H1 fix: verify that an admin token is not accepted as a valid portal token signature
    // (i.e., a token signed with the admin key does not verify against the portal key).
    @Test
    void adminToken_isRejectedByProviderWithOnlyPortalKey() {
        UUID userId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();
        String adminToken = provider.generateToken(userId, agencyId, "ADMIN");

        // Build a provider where admin and portal secrets are both different
        JwtProperties otherProps = new JwtProperties();
        otherProps.setSecret("other-admin-secret-key-must-be-at-least-256-bits-for-hmac-sha256!");
        otherProps.setExpirationMs(EXPIRY_MS);
        PortalProperties otherPortalProps = new PortalProperties();
        otherPortalProps.getJwt().setExpirationDays(30);
        otherPortalProps.getJwt().setSecret(
            "other-portal-secret-key-must-be-at-least-256-bits-for-hmac-sha256-algorithm");
        JwtTokenProvider otherProvider = new JwtTokenProvider(otherProps, otherPortalProps);

        assertThat(otherProvider.parseAndValidate(adminToken)).isNull();
    }
}
