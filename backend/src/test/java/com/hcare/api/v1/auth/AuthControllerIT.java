package com.hcare.api.v1.auth;

import com.hcare.AbstractIntegrationTest;
import com.hcare.domain.Agency;
import com.hcare.domain.AgencyRepository;
import com.hcare.domain.AgencyUser;
import com.hcare.domain.AgencyUserRepository;
import com.hcare.domain.UserRole;
import com.hcare.api.v1.auth.dto.LoginRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;

class AuthControllerIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private AgencyRepository agencyRepo;
    @Autowired private AgencyUserRepository userRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedUser() {
        userRepo.deleteAll();
        agencyRepo.deleteAll();
        Agency agency = agencyRepo.save(new Agency("Test Agency", "TX"));
        userRepo.save(new AgencyUser(
            agency.getId(), "admin@test.com",
            passwordEncoder.encode("password123"), UserRole.ADMIN));
    }

    @Test
    void anyProtectedEndpoint_withoutToken_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_withValidCredentials_returnsJwt() {
        LoginRequest request = new LoginRequest("admin@test.com", "password123");
        ResponseEntity<LoginResponse> response = restTemplate.postForEntity(
            "/api/v1/auth/login", request, LoginResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isNotBlank();
    }

    @Test
    void login_withWrongPassword_returns401() {
        LoginRequest request = new LoginRequest("admin@test.com", "wrongpassword");
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/auth/login", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_withUnknownEmail_returns401() {
        LoginRequest request = new LoginRequest("nobody@test.com", "password123");
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/auth/login", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
