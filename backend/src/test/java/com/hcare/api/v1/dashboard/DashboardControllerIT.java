package com.hcare.api.v1.dashboard;

import com.hcare.AbstractIntegrationTest;
import com.hcare.api.v1.auth.dto.LoginRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import com.hcare.api.v1.dashboard.dto.DashboardAlert;
import com.hcare.api.v1.dashboard.dto.DashboardTodayResponse;
import com.hcare.domain.Agency;
import com.hcare.domain.AgencyRepository;
import com.hcare.domain.AgencyUser;
import com.hcare.domain.AgencyUserRepository;
import com.hcare.domain.Caregiver;
import com.hcare.domain.CaregiverCredential;
import com.hcare.domain.CaregiverCredentialRepository;
import com.hcare.domain.CaregiverRepository;
import com.hcare.domain.Client;
import com.hcare.domain.ClientRepository;
import com.hcare.domain.CredentialType;
import com.hcare.domain.ServiceType;
import com.hcare.domain.ServiceTypeRepository;
import com.hcare.domain.Shift;
import com.hcare.domain.ShiftRepository;
import com.hcare.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(statements = {
    "TRUNCATE TABLE evv_records, adl_task_completions, adl_tasks, shift_offers, shifts, " +
    "background_checks, caregiver_credentials, caregiver_availability, " +
    "caregiver_client_affinities, caregiver_scoring_profiles, caregivers, " +
    "authorizations, payers, service_types, " +
    "goals, care_plans, family_portal_users, client_diagnoses, client_medications, " +
    "clients, agency_users, agencies RESTART IDENTITY CASCADE"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DashboardControllerIT extends AbstractIntegrationTest {

    private static final String ADMIN_EMAIL = "dash-admin@test.com";
    private static final String ADMIN_PASSWORD = "password123";

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private AgencyRepository agencyRepo;
    @Autowired private AgencyUserRepository userRepo;
    @Autowired private ClientRepository clientRepo;
    @Autowired private CaregiverRepository caregiverRepo;
    @Autowired private ServiceTypeRepository serviceTypeRepo;
    @Autowired private ShiftRepository shiftRepo;
    @Autowired private CaregiverCredentialRepository credentialRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    private Agency agency;

    @BeforeEach
    void seedAgencyAndAdmin() {
        agency = agencyRepo.save(new Agency("Dashboard Test Agency", "TX"));
        userRepo.save(new AgencyUser(
            agency.getId(), ADMIN_EMAIL,
            passwordEncoder.encode(ADMIN_PASSWORD), UserRole.ADMIN));
    }

    private String token() {
        LoginRequest req = new LoginRequest(ADMIN_EMAIL, ADMIN_PASSWORD);
        ResponseEntity<LoginResponse> resp = restTemplate.postForEntity(
            "/api/v1/auth/login", req, LoginResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        return resp.getBody().token();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // -------------------------------------------------------------------------
    // Test 1: empty agency — no shifts today
    // -------------------------------------------------------------------------

    @Test
    void getDashboardToday_returns_200_with_empty_visits_when_no_shifts_today() {
        ResponseEntity<DashboardTodayResponse> resp = restTemplate.exchange(
            "/api/v1/dashboard/today",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(token())),
            DashboardTodayResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        DashboardTodayResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.totalVisitsToday()).isEqualTo(0);
        assertThat(body.visits()).isEmpty();
        assertThat(body.alerts()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Test 2: one shift today — visit count and names appear correctly
    // -------------------------------------------------------------------------

    @Test
    void getDashboardToday_counts_todays_shift() {
        Client client = clientRepo.save(new Client(
            agency.getId(), "Alice", "Smith", LocalDate.of(1960, 6, 15)));
        client.setServiceState("TX");
        clientRepo.save(client);

        Caregiver caregiver = caregiverRepo.save(
            new Caregiver(agency.getId(), "Bob", "Jones", "bob@test.com"));

        ServiceType serviceType = serviceTypeRepo.save(
            new ServiceType(agency.getId(), "PCS", "PCS-V1", true, "[]"));

        LocalDateTime todayMid = LocalDate.now(ZoneOffset.UTC).atTime(10, 0);
        shiftRepo.save(new Shift(
            agency.getId(), null, client.getId(), caregiver.getId(),
            serviceType.getId(), null,
            todayMid, todayMid.plusHours(4)));

        ResponseEntity<DashboardTodayResponse> resp = restTemplate.exchange(
            "/api/v1/dashboard/today",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(token())),
            DashboardTodayResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        DashboardTodayResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.totalVisitsToday()).isEqualTo(1);
        assertThat(body.visits()).hasSize(1);
        assertThat(body.visits().get(0).clientFirstName()).isEqualTo("Alice");
        assertThat(body.visits().get(0).caregiverFirstName()).isEqualTo("Bob");
    }

    // -------------------------------------------------------------------------
    // Test 3: yesterday's shift is excluded
    // -------------------------------------------------------------------------

    @Test
    void getDashboardToday_does_not_include_yesterday_shift() {
        Client client = clientRepo.save(new Client(
            agency.getId(), "Charlie", "Brown", LocalDate.of(1945, 1, 1)));

        ServiceType serviceType = serviceTypeRepo.save(
            new ServiceType(agency.getId(), "PCS", "PCS-V1", true, "[]"));

        LocalDateTime yesterdayMid = LocalDate.now(ZoneOffset.UTC).minusDays(1).atTime(10, 0);
        shiftRepo.save(new Shift(
            agency.getId(), null, client.getId(), null,
            serviceType.getId(), null,
            yesterdayMid, yesterdayMid.plusHours(4)));

        ResponseEntity<DashboardTodayResponse> resp = restTemplate.exchange(
            "/api/v1/dashboard/today",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(token())),
            DashboardTodayResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().totalVisitsToday()).isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // Test 4: unauthenticated request returns 401
    // -------------------------------------------------------------------------

    @Test
    void getDashboardToday_returns_401_without_token() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/dashboard/today",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // -------------------------------------------------------------------------
    // Test 5: expiring credential generates CREDENTIAL_EXPIRING alert
    // -------------------------------------------------------------------------

    @Test
    void getDashboardToday_includes_expiring_credential_alert() {
        Caregiver caregiver = caregiverRepo.save(
            new Caregiver(agency.getId(), "Carol", "Davis", "carol@test.com"));

        LocalDate expiryDate = LocalDate.now(ZoneOffset.UTC).plusDays(10);
        credentialRepo.save(new CaregiverCredential(
            caregiver.getId(), agency.getId(),
            CredentialType.CPR,
            LocalDate.now(ZoneOffset.UTC).minusYears(2),
            expiryDate));

        ResponseEntity<DashboardTodayResponse> resp = restTemplate.exchange(
            "/api/v1/dashboard/today",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(token())),
            DashboardTodayResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        DashboardTodayResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.alerts()).isNotNull();

        assertThat(body.alerts())
            .anyMatch(alert ->
                "CREDENTIAL_EXPIRING".equals(alert.alertType())
                && alert.subjectName() != null
                && alert.subjectName().contains("Carol"));
    }
}
