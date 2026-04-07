package com.hcare.api.v1.agencies;

import com.hcare.AbstractIntegrationTest;
import com.hcare.api.v1.agencies.dto.AgencyResponse;
import com.hcare.api.v1.agencies.dto.RegisterAgencyRequest;
import com.hcare.api.v1.agencies.dto.UpdateAgencyRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import com.hcare.domain.AgencyRepository;
import com.hcare.domain.AgencyUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(statements = {
    "TRUNCATE TABLE feature_flags, agency_users, agencies RESTART IDENTITY CASCADE"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AgencyControllerIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private AgencyRepository agencyRepo;
    @Autowired private AgencyUserRepository userRepo;

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
}
