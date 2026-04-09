package com.hcare.api.v1.family;

import com.hcare.AbstractIntegrationTest;
import com.hcare.api.v1.auth.dto.LoginRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import com.hcare.api.v1.family.dto.InviteResponse;
import com.hcare.api.v1.family.dto.PortalVerifyRequest;
import com.hcare.api.v1.family.dto.PortalVerifyResponse;
import com.hcare.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(statements = {
    "TRUNCATE TABLE family_portal_tokens, family_portal_users, clients, agency_users, agencies RESTART IDENTITY CASCADE"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class FamilyPortalAuthControllerIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private AgencyRepository agencyRepo;
    @Autowired private AgencyUserRepository userRepo;
    @Autowired private ClientRepository clientRepo;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private FamilyPortalTokenRepository tokenRepo;

    private UUID clientId;
    private String adminToken;

    @BeforeEach
    void seed() {
        Agency agency = agencyRepo.save(new Agency("Portal Auth IT Agency", "NY"));
        userRepo.save(new AgencyUser(agency.getId(), "admin@portalit.com",
            passwordEncoder.encode("Pass1234!"), UserRole.ADMIN));
        Client client = clientRepo.save(
            new Client(agency.getId(), "Margaret", "Test", LocalDate.of(1940, 1, 1)));
        clientId = client.getId();
        adminToken = restTemplate.postForEntity("/api/v1/auth/login",
            new LoginRequest("admin@portalit.com", "Pass1234!"), LoginResponse.class)
            .getBody().token();
    }

    private HttpHeaders adminAuth() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(adminToken);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    void inviteThenVerify_happyPath_returnsPortalJwt() {
        // Generate invite
        ResponseEntity<InviteResponse> inviteResp = restTemplate.exchange(
            "/api/v1/clients/" + clientId + "/family-portal-users/invite",
            HttpMethod.POST,
            new HttpEntity<>("{\"email\":\"family@example.com\"}", adminAuth()),
            InviteResponse.class);
        assertThat(inviteResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(inviteResp.getBody().inviteUrl()).contains("/portal/verify?token=");

        // Extract raw token from URL — use UriComponentsBuilder to correctly handle
        // multi-param query strings; String.replace would corrupt the token in that case.
        String rawToken = UriComponentsBuilder
            .fromUriString(inviteResp.getBody().inviteUrl())
            .build()
            .getQueryParams()
            .getFirst("token");

        // Verify token
        ResponseEntity<PortalVerifyResponse> verifyResp = restTemplate.postForEntity(
            "/api/v1/family/auth/verify",
            new PortalVerifyRequest(rawToken),
            PortalVerifyResponse.class);
        assertThat(verifyResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verifyResp.getBody().jwt()).isNotBlank();
        assertThat(verifyResp.getBody().clientId()).isEqualTo(clientId.toString());
    }

    @Test
    void verify_withInvalidToken_returns400() {
        ResponseEntity<String> resp = restTemplate.postForEntity(
            "/api/v1/family/auth/verify",
            new PortalVerifyRequest("deadbeefdeadbeef"),
            String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void verify_oneTimeUse_secondCallFails() {
        // Generate invite
        ResponseEntity<InviteResponse> inviteResp = restTemplate.exchange(
            "/api/v1/clients/" + clientId + "/family-portal-users/invite",
            HttpMethod.POST,
            new HttpEntity<>("{\"email\":\"family2@example.com\"}", adminAuth()),
            InviteResponse.class);
        String rawToken = UriComponentsBuilder
            .fromUriString(inviteResp.getBody().inviteUrl())
            .build()
            .getQueryParams()
            .getFirst("token");

        // First verify succeeds
        ResponseEntity<PortalVerifyResponse> first = restTemplate.postForEntity(
            "/api/v1/family/auth/verify", new PortalVerifyRequest(rawToken), PortalVerifyResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second verify fails — token row deleted
        ResponseEntity<String> second = restTemplate.postForEntity(
            "/api/v1/family/auth/verify", new PortalVerifyRequest(rawToken), String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void invite_storesSha256Hash_notRawToken() {
        // Generate invite
        ResponseEntity<InviteResponse> inviteResp = restTemplate.exchange(
            "/api/v1/clients/" + clientId + "/family-portal-users/invite",
            HttpMethod.POST,
            new HttpEntity<>("{\"email\":\"hash-check@example.com\"}", adminAuth()),
            InviteResponse.class);
        assertThat(inviteResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Extract raw token from invite URL (128 hex chars = 64 bytes)
        String rawToken = UriComponentsBuilder
            .fromUriString(inviteResp.getBody().inviteUrl())
            .build()
            .getQueryParams()
            .getFirst("token");
        assertThat(rawToken).hasSize(128);

        // Compute SHA-256(rawToken) in the test — matches FamilyPortalService.sha256Hex()
        String expectedHash = sha256Hex(rawToken);

        // Assert the hash (not the raw token) is stored in the database
        assertThat(tokenRepo.findByTokenHash(expectedHash))
            .as("SHA-256 hash should be stored in database")
            .isPresent();

        // Assert the raw token is NOT stored in the database
        assertThat(tokenRepo.findByTokenHash(rawToken))
            .as("Raw token should NOT be stored in database")
            .isEmpty();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
