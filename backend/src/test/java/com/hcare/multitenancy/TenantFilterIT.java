package com.hcare.multitenancy;

import com.hcare.AbstractIntegrationTest;
import com.hcare.domain.*;
import com.hcare.api.v1.auth.dto.LoginRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Arrays;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class TenantFilterIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private AgencyRepository agencyRepo;
    @Autowired private AgencyUserRepository userRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    private String tokenA;
    private UUID agencyAUserId;
    private UUID agencyBUserId;

    @BeforeEach
    void setup() {
        userRepo.deleteAll();
        agencyRepo.deleteAll();

        Agency agencyA = agencyRepo.save(new Agency("Agency A", "TX"));
        Agency agencyB = agencyRepo.save(new Agency("Agency B", "CA"));

        AgencyUser userA = userRepo.save(new AgencyUser(agencyA.getId(), "admin-a@test.com",
            passwordEncoder.encode("pass"), UserRole.ADMIN));
        AgencyUser userB = userRepo.save(new AgencyUser(agencyB.getId(), "admin-b@test.com",
            passwordEncoder.encode("pass"), UserRole.ADMIN));

        agencyAUserId = userA.getId();
        agencyBUserId = userB.getId();

        LoginResponse login = restTemplate.postForObject(
            "/api/v1/auth/login",
            new LoginRequest("admin-a@test.com", "pass"),
            LoginResponse.class);
        tokenA = login.token();
    }

    @Test
    void authenticatedRequest_onlySeesOwnAgencyUsers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);

        ResponseEntity<UUID[]> response = restTemplate.exchange(
            "/api/v1/users", HttpMethod.GET,
            new HttpEntity<>(headers), UUID[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID[] userIds = response.getBody();
        assertThat(userIds).isNotNull();
        assertThat(Arrays.asList(userIds)).contains(agencyAUserId);
        assertThat(Arrays.asList(userIds)).doesNotContain(agencyBUserId);
        assertThat(userIds).hasSize(1);
    }
}
