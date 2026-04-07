package com.hcare.api.v1.agencies;

import com.hcare.AbstractIntegrationTest;
import com.hcare.api.v1.agencies.dto.AgencyResponse;
import com.hcare.api.v1.agencies.dto.RegisterAgencyRequest;
import com.hcare.api.v1.agencies.dto.UpdateAgencyRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import com.hcare.domain.AgencyRepository;
import com.hcare.domain.AgencyUser;
import com.hcare.domain.AgencyUserRepository;
import com.hcare.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(statements = {
    "TRUNCATE TABLE feature_flags, agency_users, agencies RESTART IDENTITY CASCADE"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AgencyControllerIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private AgencyRepository agencyRepo;
    @Autowired private AgencyUserRepository userRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void register_creates_agency_and_returns_jwt() {
        RegisterAgencyRequest req = new RegisterAgencyRequest(
            "Sunrise Home Care", "TX", "admin@sunrise.com", "Str0ngP@ss!");

        ResponseEntity<LoginResponse> resp = restTemplate.postForEntity(
            "/api/v1/agencies/register", req, LoginResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().token()).isNotBlank();
        assertThat(resp.getBody().agencyId()).isNotNull();
        assertThat(agencyRepo.count()).isEqualTo(1);
        assertThat(userRepo.count()).isEqualTo(1);
    }

    @Test
    void register_returns_409_for_duplicate_email() {
        RegisterAgencyRequest req = new RegisterAgencyRequest(
            "Agency A", "TX", "dup@test.com", "Str0ngP@ss!");
        restTemplate.postForEntity("/api/v1/agencies/register", req, LoginResponse.class);

        RegisterAgencyRequest dup = new RegisterAgencyRequest(
            "Agency B", "CA", "dup@test.com", "Str0ngP@ss!");
        ResponseEntity<String> resp = restTemplate.postForEntity(
            "/api/v1/agencies/register", dup, String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void register_returns_400_for_invalid_state() {
        RegisterAgencyRequest req = new RegisterAgencyRequest(
            "Agency", "TEXAS", "x@test.com", "Str0ngP@ss!");
        ResponseEntity<String> resp = restTemplate.postForEntity(
            "/api/v1/agencies/register", req, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void register_returns_400_for_null_state() {
        var body = Map.of(
            "agencyName", "Null State Agency",
            "adminEmail", "null@test.com",
            "adminPassword", "Str0ngP@ss!"
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = restTemplate.postForEntity(
            "/api/v1/agencies/register", new HttpEntity<>(body, headers), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getMyAgency_returns_agency_for_authenticated_user() {
        RegisterAgencyRequest req = new RegisterAgencyRequest(
            "Blue Ridge Care", "NC", "admin@blueridge.com", "Str0ngP@ss!");
        LoginResponse login = restTemplate.postForEntity(
            "/api/v1/agencies/register", req, LoginResponse.class).getBody();

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(login.token());
        ResponseEntity<AgencyResponse> resp = restTemplate.exchange(
            "/api/v1/agencies/me", HttpMethod.GET, new HttpEntity<>(h), AgencyResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().name()).isEqualTo("Blue Ridge Care");
        assertThat(resp.getBody().state()).isEqualTo("NC");
    }

    @Test
    void updateMyAgency_changes_name() {
        RegisterAgencyRequest req = new RegisterAgencyRequest(
            "Old Name Care", "FL", "admin@oldname.com", "Str0ngP@ss!");
        LoginResponse login = restTemplate.postForEntity(
            "/api/v1/agencies/register", req, LoginResponse.class).getBody();

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(login.token());
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<AgencyResponse> resp = restTemplate.exchange(
            "/api/v1/agencies/me", HttpMethod.PATCH,
            new HttpEntity<>(new UpdateAgencyRequest("New Name Care", null), h),
            AgencyResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().name()).isEqualTo("New Name Care");
    }

    @Test
    void getMyAgency_returns_401_without_token() {
        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/agencies/me", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void updateMyAgency_returns_403_for_scheduler() {
        // Register agency (creates ADMIN)
        RegisterAgencyRequest reg = new RegisterAgencyRequest(
            "Sched403 Agency", "TX", "admin@sched403.com", "Str0ngP@ss!");
        LoginResponse adminLogin = restTemplate.postForEntity(
            "/api/v1/agencies/register", reg, LoginResponse.class).getBody();

        // Seed a SCHEDULER for the same agency directly
        userRepo.save(new AgencyUser(adminLogin.agencyId(), "sched@sched403.com",
            passwordEncoder.encode("Str0ngP@ss!"), UserRole.SCHEDULER));

        // Log in as the scheduler
        LoginResponse schedLogin = restTemplate.postForEntity("/api/v1/auth/login",
            new com.hcare.api.v1.auth.dto.LoginRequest("sched@sched403.com", "Str0ngP@ss!"),
            LoginResponse.class).getBody();

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(schedLogin.token());
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/agencies/me", HttpMethod.PATCH,
            new HttpEntity<>(new UpdateAgencyRequest("Hacked Name", null), h), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
