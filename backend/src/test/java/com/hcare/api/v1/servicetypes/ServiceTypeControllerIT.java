package com.hcare.api.v1.servicetypes;

import com.hcare.AbstractIntegrationTest;
import com.hcare.api.v1.auth.dto.LoginRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import com.hcare.api.v1.servicetypes.dto.ServiceTypeResponse;
import com.hcare.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(statements = {
    "TRUNCATE TABLE shift_offers, shifts, recurrence_patterns, authorizations, caregiver_scoring_profiles, " +
    "caregiver_client_affinities, caregivers, service_types, clients, agency_users, agencies RESTART IDENTITY CASCADE"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ServiceTypeControllerIT extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "correcthorsebatterystaple";

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private AgencyRepository agencyRepo;
    @Autowired private AgencyUserRepository userRepo;
    @Autowired private ServiceTypeRepository serviceTypeRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    private Agency agencyA;
    private Agency agencyB;
    private Agency agencyC;

    @BeforeEach
    void seed() {
        // Agency A — two service types
        agencyA = agencyRepo.save(new Agency("Agency Alpha", "TX"));
        userRepo.save(new AgencyUser(agencyA.getId(), "admin-a@test.com",
            passwordEncoder.encode(TEST_PASSWORD), UserRole.ADMIN));
        serviceTypeRepo.save(new ServiceType(agencyA.getId(), "Skilled Nursing Visit", "SNV", true, "[]"));
        serviceTypeRepo.save(new ServiceType(agencyA.getId(), "Personal Care Services", "PCS", false, "[]"));

        // Agency B — one service type (different from A)
        agencyB = agencyRepo.save(new Agency("Agency Beta", "TX"));
        userRepo.save(new AgencyUser(agencyB.getId(), "admin-b@test.com",
            passwordEncoder.encode(TEST_PASSWORD), UserRole.ADMIN));
        serviceTypeRepo.save(new ServiceType(agencyB.getId(), "Homemaker Services", "HM", false, "[]"));

        // Agency C — no service types; user seeded so the empty-list test can authenticate
        agencyC = agencyRepo.save(new Agency("Agency Gamma", "TX"));
        userRepo.save(new AgencyUser(agencyC.getId(), "admin-c@test.com",
            passwordEncoder.encode(TEST_PASSWORD), UserRole.ADMIN));
    }

    private String tokenFor(String email) {
        LoginRequest req = new LoginRequest(email, TEST_PASSWORD);
        return restTemplate.postForEntity("/api/v1/auth/login", req, LoginResponse.class)
            .getBody().token();
    }

    private HttpHeaders authFor(String email) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(tokenFor(email));
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    void listServiceTypes_returns_agencyA_types_sorted_alphabetically() {
        ResponseEntity<List<ServiceTypeResponse>> resp = restTemplate.exchange(
            "/api/v1/service-types", HttpMethod.GET,
            new HttpEntity<>(authFor("admin-a@test.com")),
            new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(2);
        // Alphabetical: "Personal Care Services" before "Skilled Nursing Visit"
        assertThat(resp.getBody().get(0).name()).isEqualTo("Personal Care Services");
        assertThat(resp.getBody().get(1).name()).isEqualTo("Skilled Nursing Visit");
    }

    @Test
    void listServiceTypes_tenant_isolation_agencyB_sees_only_own_types() {
        ResponseEntity<List<ServiceTypeResponse>> resp = restTemplate.exchange(
            "/api/v1/service-types", HttpMethod.GET,
            new HttpEntity<>(authFor("admin-b@test.com")),
            new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
        assertThat(resp.getBody().get(0).name()).isEqualTo("Homemaker Services");
    }

    @Test
    void listServiceTypes_returns_empty_array_for_agency_with_no_types() {
        ResponseEntity<List<ServiceTypeResponse>> resp = restTemplate.exchange(
            "/api/v1/service-types", HttpMethod.GET,
            new HttpEntity<>(authFor("admin-c@test.com")),
            new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEmpty();
    }

    @Test
    void listServiceTypes_returns_401_without_token() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/service-types", HttpMethod.GET,
            new HttpEntity<>(h), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
