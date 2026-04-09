package com.hcare.security;

import com.hcare.config.PortalProperties;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;
    private JwtTokenProvider tokenProvider;
    private final String secret =
        "test-secret-key-must-be-at-least-256-bits-for-hmac-sha256-algorithm-ok";

    @Mock private FilterChain chain;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        JwtProperties props = new JwtProperties();
        props.setSecret(secret);
        props.setExpirationMs(86_400_000L);
        // C1 fix: use PortalProperties (hcare.portal) not JwtProperties (hcare.jwt)
        PortalProperties portalProps = new PortalProperties();
        portalProps.getJwt().setExpirationDays(30);
        tokenProvider = new JwtTokenProvider(props, portalProps);
        filter = new JwtAuthenticationFilter(tokenProvider);
        SecurityContextHolder.clearContext();
    }

    @Test
    void familyPortalToken_populatesClientIdOnPrincipal() throws Exception {
        UUID fpuId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();
        String jwt = tokenProvider.generateFamilyPortalToken(fpuId, clientId, agencyId);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        assertThat(principal.getRole()).isEqualTo("FAMILY_PORTAL");
        assertThat(principal.getClientId()).isEqualTo(clientId);
        assertThat(principal.getUserId()).isEqualTo(fpuId);
        assertThat(principal.getAgencyId()).isEqualTo(agencyId);
    }

    @Test
    void adminToken_leavesClientIdNull() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();
        String jwt = tokenProvider.generateToken(userId, agencyId, "ADMIN");

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, chain);

        UserPrincipal principal = (UserPrincipal)
            SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal.getRole()).isEqualTo("ADMIN");
        assertThat(principal.getClientId()).isNull();
    }

    @Test
    void familyPortalToken_missingClientIdClaim_failsClosed() throws Exception {
        // C2 fix: a FAMILY_PORTAL token that lacks a clientId claim must be rejected —
        // the filter must NOT place any authentication in the SecurityContextHolder.
        // Build a raw FAMILY_PORTAL JWT without a clientId claim to simulate a malformed token.
        UUID fpuId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();
        // Use the admin token generator but override role to FAMILY_PORTAL (no clientId claim added).
        // We manually craft the token via JwtTokenProvider internals exposed for testing,
        // or — simpler — rely on the fact that generateToken() with role "FAMILY_PORTAL" omits clientId.
        String malformedJwt = tokenProvider.generateToken(fpuId, agencyId, "FAMILY_PORTAL");

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + malformedJwt);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, chain);

        // Fail closed: no authentication must be present in the SecurityContext
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void validlySignedToken_missingAgencyIdClaim_noAuthenticationSet() throws Exception {
        // C5 fix: a validly-signed token missing the agencyId claim must result in
        // no authentication being set and a chain.doFilter pass-through (not a 500).
        // We simulate this by crafting a raw token via JwtTokenProvider using a role-only
        // token (generateToken omits no required claim, so we use a mock Claims approach).
        // The simplest way: use Mockito to return null for agencyId from a spy on parseAndValidate.
        JwtTokenProvider spyProvider = org.mockito.Mockito.spy(tokenProvider);
        io.jsonwebtoken.Claims mockClaims = org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class);
        org.mockito.Mockito.when(mockClaims.getSubject()).thenReturn(UUID.randomUUID().toString());
        org.mockito.Mockito.when(mockClaims.get("agencyId", String.class)).thenReturn(null); // missing
        org.mockito.Mockito.when(mockClaims.get("role", String.class)).thenReturn("ADMIN");
        org.mockito.Mockito.doReturn(mockClaims).when(spyProvider).parseAndValidate(anyString());

        JwtAuthenticationFilter spyFilter = new JwtAuthenticationFilter(spyProvider);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer some.fake.token");
        MockHttpServletResponse res = new MockHttpServletResponse();

        spyFilter.doFilterInternal(req, res, chain);

        // Must not set authentication — missing agencyId is rejected with warn log, not 500
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
