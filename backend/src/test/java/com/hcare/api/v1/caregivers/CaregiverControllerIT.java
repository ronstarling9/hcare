package com.hcare.api.v1.caregivers;

import com.hcare.AbstractIntegrationTest;
import com.hcare.api.v1.auth.dto.LoginRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import com.hcare.api.v1.caregivers.dto.*;
import com.hcare.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(statements = {
    "TRUNCATE TABLE caregiver_credentials, background_checks, caregiver_availability, " +
    "caregiver_scoring_profiles, caregiver_client_affinities, caregivers, agency_users, agencies " +
    "RESTART IDENTITY CASCADE"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class CaregiverControllerIT extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "correcthorsebatterystaple";

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private AgencyRepository agencyRepo;
    @Autowired private AgencyUserRepository userRepo;
    @Autowired private CaregiverRepository caregiverRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    private Agency agency;
    private Caregiver caregiver;

    @BeforeEach
    void seed() {
        agency = agencyRepo.save(new Agency("CG IT Agency", "TX"));
        userRepo.save(new AgencyUser(agency.getId(), "admin@cgit.com",
            passwordEncoder.encode(TEST_PASSWORD), UserRole.ADMIN));
        caregiver = caregiverRepo.save(new Caregiver(agency.getId(), "John", "Doe", "john@test.com"));
    }

    private String token() {
        return restTemplate.postForEntity("/api/v1/auth/login",
            new LoginRequest("admin@cgit.com", TEST_PASSWORD), LoginResponse.class)
            .getBody().token();
    }

    private HttpHeaders auth() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token());
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    void listCaregivers_returns_only_agency_caregivers() {
        Agency other = agencyRepo.save(new Agency("Other", "CA"));
        caregiverRepo.save(new Caregiver(other.getId(), "Other", "CG", "other@test.com"));

        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
            "/api/v1/caregivers", HttpMethod.GET,
            new HttpEntity<>(auth()), new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Integer) resp.getBody().get("totalElements")).isEqualTo(1);
    }

    @Test
    void createCaregiver_returns_201() {
        CreateCaregiverRequest req = new CreateCaregiverRequest(
            "Jane", "Smith", "jane@test.com", null, null, null, null);

        ResponseEntity<CaregiverResponse> resp = restTemplate.exchange(
            "/api/v1/caregivers", HttpMethod.POST,
            new HttpEntity<>(req, auth()), CaregiverResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().id()).isNotNull();
        assertThat(resp.getBody().email()).isEqualTo("jane@test.com");
    }

    @Test
    void getCaregiver_returns_404_for_other_agency() {
        Agency other = agencyRepo.save(new Agency("Other", "CA"));
        Caregiver otherCg = caregiverRepo.save(new Caregiver(other.getId(), "X", "Y", "xy@test.com"));

        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/caregivers/" + otherCg.getId(), HttpMethod.GET,
            new HttpEntity<>(auth()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateCaregiver_patches_email() {
        UpdateCaregiverRequest req = new UpdateCaregiverRequest(
            null, null, "updated@test.com", null, null, null, null, null);

        ResponseEntity<CaregiverResponse> resp = restTemplate.exchange(
            "/api/v1/caregivers/" + caregiver.getId(), HttpMethod.PATCH,
            new HttpEntity<>(req, auth()), CaregiverResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().email()).isEqualTo("updated@test.com");
    }

    @Test
    void setAvailability_and_retrieve_it() {
        SetAvailabilityRequest req = new SetAvailabilityRequest(List.of(
            new AvailabilityBlockRequest(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(16, 0))));

        ResponseEntity<AvailabilityResponse> setResp = restTemplate.exchange(
            "/api/v1/caregivers/" + caregiver.getId() + "/availability", HttpMethod.PUT,
            new HttpEntity<>(req, auth()), AvailabilityResponse.class);
        assertThat(setResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<AvailabilityResponse> getResp = restTemplate.exchange(
            "/api/v1/caregivers/" + caregiver.getId() + "/availability", HttpMethod.GET,
            new HttpEntity<>(auth()), AvailabilityResponse.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().blocks()).hasSize(1);
        assertThat(getResp.getBody().blocks().get(0).dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void addAndListCredential_for_caregiver() {
        AddCredentialRequest req = new AddCredentialRequest(
            CredentialType.CPR, LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1));
        ResponseEntity<CredentialResponse> add = restTemplate.exchange(
            "/api/v1/caregivers/" + caregiver.getId() + "/credentials", HttpMethod.POST,
            new HttpEntity<>(req, auth()), CredentialResponse.class);
        assertThat(add.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(add.getBody().credentialType()).isEqualTo(CredentialType.CPR);
    }

    @Test
    void deleteCredential_from_other_caregiver_returns_404() {
        Caregiver other = caregiverRepo.save(new Caregiver(agency.getId(), "A", "B", "ab@test.com"));
        AddCredentialRequest req = new AddCredentialRequest(
            CredentialType.CPR, LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1));
        CredentialResponse cred = restTemplate.exchange(
            "/api/v1/caregivers/" + other.getId() + "/credentials", HttpMethod.POST,
            new HttpEntity<>(req, auth()), CredentialResponse.class).getBody();

        ResponseEntity<String> del = restTemplate.exchange(
            "/api/v1/caregivers/" + caregiver.getId() + "/credentials/" + cred.id(),
            HttpMethod.DELETE, new HttpEntity<>(auth()), String.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
