package com.hcare.api.v1.visits;

import com.hcare.AbstractIntegrationTest;
import com.hcare.api.v1.auth.dto.LoginRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import com.hcare.api.v1.visits.dto.ClockInRequest;
import com.hcare.api.v1.visits.dto.ClockOutRequest;
import com.hcare.api.v1.visits.dto.ShiftDetailResponse;
import com.hcare.domain.*;
import com.hcare.evv.VerificationMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(statements = {
    "TRUNCATE TABLE evv_records, shifts, authorizations, payers, service_types, clients, agency_users, agencies RESTART IDENTITY CASCADE"
},
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class VisitControllerIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private AgencyRepository agencyRepo;
    @Autowired private AgencyUserRepository userRepo;
    @Autowired private ClientRepository clientRepo;
    @Autowired private ServiceTypeRepository serviceTypeRepo;
    @Autowired private ShiftRepository shiftRepo;
    @Autowired private PayerRepository payerRepo;
    @Autowired private AuthorizationRepository authorizationRepo;
    @Autowired private EvvRecordRepository evvRecordRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    private Agency agency;
    private AgencyUser adminUser;
    private Client client;
    private ServiceType serviceType;

    @BeforeEach
    void seedBaseData() {
        agency = agencyRepo.save(new Agency("Visit Test Agency", "TX"));
        adminUser = userRepo.save(new AgencyUser(
            agency.getId(), "visit-admin@test.com",
            passwordEncoder.encode("password123"), UserRole.ADMIN));
        client = clientRepo.save(new Client(agency.getId(), "Jane", "Doe", LocalDate.of(1955, 3, 10)));
        client.setMedicaidId("TX-MED-12345");
        // Austin, TX coordinates
        client.setLat(new BigDecimal("30.2672"));
        client.setLng(new BigDecimal("-97.7431"));
        clientRepo.save(client);
        serviceType = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-V1", true, "[]"));
    }

    private String loginAndGetToken() {
        LoginRequest req = new LoginRequest("visit-admin@test.com", "password123");
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

    private Shift createAssignedShift(UUID caregiverId, LocalDateTime start, LocalDateTime end) {
        return shiftRepo.save(new Shift(
            agency.getId(), null, client.getId(), caregiverId,
            serviceType.getId(), null, start, end));
    }

    // -------------------------------------------------------------------------
    // Clock-in tests
    // -------------------------------------------------------------------------

    @Test
    void clockIn_happy_path_creates_evv_record_and_returns_in_progress() {
        UUID caregiverId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Shift shift = createAssignedShift(caregiverId, now.minusMinutes(5), now.plusHours(4));

        String token = loginAndGetToken();
        ClockInRequest req = new ClockInRequest(
            new BigDecimal("30.2672"), new BigDecimal("-97.7431"),
            VerificationMethod.GPS, false, null);

        ResponseEntity<ShiftDetailResponse> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/clock-in",
            HttpMethod.POST,
            new HttpEntity<>(req, authHeaders(token)),
            ShiftDetailResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        ShiftDetailResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo("IN_PROGRESS");
        assertThat(body.evv()).isNotNull();
        assertThat(body.evv().timeIn()).isNotNull();
        assertThat(body.evv().timeOut()).isNull();
        // No timeOut yet → RED (missing timeOut is a RED condition)
        assertThat(body.evv().complianceStatus()).isEqualTo("RED");
        assertThat(body.evv().evvRecordId()).isNotNull();
    }

    @Test
    void clockIn_on_nonexistent_shift_returns_404() {
        String token = loginAndGetToken();
        ClockInRequest req = new ClockInRequest(
            new BigDecimal("30.2672"), new BigDecimal("-97.7431"),
            VerificationMethod.GPS, false, null);

        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/shifts/" + UUID.randomUUID() + "/clock-in",
            HttpMethod.POST,
            new HttpEntity<>(req, authHeaders(token)),
            String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void clockIn_twice_returns_409() {
        UUID caregiverId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Shift shift = createAssignedShift(caregiverId, now.minusMinutes(5), now.plusHours(4));

        String token = loginAndGetToken();
        ClockInRequest req = new ClockInRequest(
            new BigDecimal("30.2672"), new BigDecimal("-97.7431"),
            VerificationMethod.GPS, false, null);

        // First clock-in should succeed
        ResponseEntity<ShiftDetailResponse> first = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/clock-in",
            HttpMethod.POST, new HttpEntity<>(req, authHeaders(token)), ShiftDetailResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second clock-in should conflict
        ResponseEntity<String> second = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/clock-in",
            HttpMethod.POST, new HttpEntity<>(req, authHeaders(token)), String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void clockIn_without_auth_returns_401() {
        UUID caregiverId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Shift shift = createAssignedShift(caregiverId, now.minusMinutes(5), now.plusHours(4));

        ClockInRequest req = new ClockInRequest(
            new BigDecimal("30.2672"), new BigDecimal("-97.7431"),
            VerificationMethod.GPS, false, null);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/clock-in",
            HttpMethod.POST, new HttpEntity<>(req, headers), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void clockIn_on_open_shift_returns_409() {
        // OPEN shift: no caregiverId
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Shift shift = shiftRepo.save(new Shift(
            agency.getId(), null, client.getId(), null,
            serviceType.getId(), null, now.minusMinutes(5), now.plusHours(4)));
        assertThat(shift.getStatus()).isEqualTo(ShiftStatus.OPEN);

        String token = loginAndGetToken();
        ClockInRequest req = new ClockInRequest(
            new BigDecimal("30.2672"), new BigDecimal("-97.7431"),
            VerificationMethod.GPS, false, null);

        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/clock-in",
            HttpMethod.POST, new HttpEntity<>(req, authHeaders(token)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void clockIn_on_completed_shift_returns_409() {
        UUID caregiverId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Shift shift = createAssignedShift(caregiverId, now.minusHours(5), now.minusHours(1));
        shift.setStatus(ShiftStatus.COMPLETED);
        shiftRepo.save(shift);

        String token = loginAndGetToken();
        ClockInRequest req = new ClockInRequest(
            new BigDecimal("30.2672"), new BigDecimal("-97.7431"),
            VerificationMethod.GPS, false, null);

        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/clock-in",
            HttpMethod.POST, new HttpEntity<>(req, authHeaders(token)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // -------------------------------------------------------------------------
    // Get shift detail tests
    // -------------------------------------------------------------------------

    @Test
    void getShiftDetail_returns_422_when_state_has_no_evv_config() {
        // Set client's serviceState to a non-existent state code "ZZ"
        client.setServiceState("ZZ");
        clientRepo.save(client);

        UUID caregiverId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Shift shift = createAssignedShift(caregiverId, now.minusMinutes(5), now.plusHours(4));

        String token = loginAndGetToken();
        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId(),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(token)),
            String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void getShiftDetail_returns_shift_with_grey_compliance_before_clockIn() {
        UUID caregiverId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Shift shift = createAssignedShift(caregiverId, now.plusHours(1), now.plusHours(5));

        String token = loginAndGetToken();
        ResponseEntity<ShiftDetailResponse> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId(),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(token)),
            ShiftDetailResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        ShiftDetailResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.evv().complianceStatus()).isEqualTo("GREY");
        assertThat(body.evv().evvRecordId()).isNull();
        assertThat(body.evv().timeIn()).isNull();
    }

    @Test
    void getShiftDetail_returns_404_for_unknown_shift() {
        String token = loginAndGetToken();
        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/shifts/" + UUID.randomUUID(),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(token)),
            String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // -------------------------------------------------------------------------
    // Clock-out tests
    // -------------------------------------------------------------------------

    @Test
    void clockOut_after_clockIn_sets_completed_and_computes_green() {
        UUID caregiverId = UUID.randomUUID();
        // Schedule start = now - 5 min so clock-in time (now) is within 30 min of scheduledStart
        // TX has no GPS tolerance and GPS is an allowed method → GREEN
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime scheduledStart = now.minusMinutes(5);
        Shift shift = createAssignedShift(caregiverId, scheduledStart, scheduledStart.plusHours(4));

        String token = loginAndGetToken();

        // Clock-in with matching GPS coordinates (Austin TX) and a medicaid ID set on the client
        ClockInRequest clockInReq = new ClockInRequest(
            new BigDecimal("30.2672"), new BigDecimal("-97.7431"),
            VerificationMethod.GPS, false, null);
        ResponseEntity<ShiftDetailResponse> clockInResp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/clock-in",
            HttpMethod.POST, new HttpEntity<>(clockInReq, authHeaders(token)),
            ShiftDetailResponse.class);
        assertThat(clockInResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Clock-out
        ClockOutRequest clockOutReq = new ClockOutRequest(false, null);
        ResponseEntity<ShiftDetailResponse> clockOutResp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/clock-out",
            HttpMethod.POST, new HttpEntity<>(clockOutReq, authHeaders(token)),
            ShiftDetailResponse.class);

        assertThat(clockOutResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        ShiftDetailResponse body = clockOutResp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo("COMPLETED");
        assertThat(body.evv().timeOut()).isNotNull();
        assertThat(body.evv().complianceStatus()).isEqualTo("GREEN");
    }

    @Test
    void clockOut_without_prior_clockIn_returns_409() {
        UUID caregiverId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        // Manually set to IN_PROGRESS but no EVV record
        Shift shift = createAssignedShift(caregiverId, now.minusMinutes(5), now.plusHours(4));
        shift.setStatus(ShiftStatus.IN_PROGRESS);
        shiftRepo.save(shift);

        String token = loginAndGetToken();
        ClockOutRequest req = new ClockOutRequest(false, null);
        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/clock-out",
            HttpMethod.POST, new HttpEntity<>(req, authHeaders(token)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // -------------------------------------------------------------------------
    // Offline capture tests
    // -------------------------------------------------------------------------

    @Test
    void clockIn_offline_uses_device_captured_at_as_time_in() {
        UUID caregiverId = UUID.randomUUID();
        LocalDateTime scheduledStart = LocalDateTime.of(2026, 4, 5, 9, 0);
        Shift shift = createAssignedShift(caregiverId, scheduledStart, scheduledStart.plusHours(4));

        LocalDateTime deviceTime = LocalDateTime.of(2026, 4, 5, 9, 3);

        String token = loginAndGetToken();
        ClockInRequest req = new ClockInRequest(
            new BigDecimal("30.2672"), new BigDecimal("-97.7431"),
            VerificationMethod.GPS, true, deviceTime);

        ResponseEntity<ShiftDetailResponse> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/clock-in",
            HttpMethod.POST, new HttpEntity<>(req, authHeaders(token)),
            ShiftDetailResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        ShiftDetailResponse body = resp.getBody();
        assertThat(body).isNotNull();
        // timeIn should exactly match deviceCapturedAt
        assertThat(body.evv().timeIn()).isEqualTo(deviceTime);
    }

    // -------------------------------------------------------------------------
    // Authorization unit tracking tests
    // -------------------------------------------------------------------------

    @Test
    void clockOut_updates_authorization_used_units_for_hours_payer() {
        Payer payer = payerRepo.save(new Payer(agency.getId(), "Medicaid TX", PayerType.MEDICAID, "TX"));
        Authorization auth = authorizationRepo.save(new Authorization(
            client.getId(), payer.getId(), serviceType.getId(), agency.getId(),
            "AUTH-HOURS-01", new BigDecimal("100.0"),
            UnitType.HOURS, LocalDate.now().minusDays(30), LocalDate.now().plusDays(30)));

        UUID caregiverId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        // Shift with authorization linked
        Shift shift = shiftRepo.save(new Shift(
            agency.getId(), null, client.getId(), caregiverId,
            serviceType.getId(), auth.getId(),
            now.minusMinutes(5), now.plusHours(4)));

        String token = loginAndGetToken();

        // Clock-in
        ClockInRequest clockInReq = new ClockInRequest(
            new BigDecimal("30.2672"), new BigDecimal("-97.7431"),
            VerificationMethod.GPS, false, null);
        restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/clock-in",
            HttpMethod.POST, new HttpEntity<>(clockInReq, authHeaders(token)),
            ShiftDetailResponse.class);

        // Clock-out
        ClockOutRequest clockOutReq = new ClockOutRequest(false, null);
        ResponseEntity<ShiftDetailResponse> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/clock-out",
            HttpMethod.POST, new HttpEntity<>(clockOutReq, authHeaders(token)),
            ShiftDetailResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Authorization updated = authorizationRepo.findById(auth.getId()).orElseThrow();
        assertThat(updated.getUsedUnits()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void clockOut_updates_authorization_used_units_for_visits_payer() {
        Payer payer = payerRepo.save(new Payer(agency.getId(), "VA Payer", PayerType.VA, "TX"));
        Authorization auth = authorizationRepo.save(new Authorization(
            client.getId(), payer.getId(), serviceType.getId(), agency.getId(),
            "AUTH-VISITS-01", new BigDecimal("20.0"),
            UnitType.VISITS, LocalDate.now().minusDays(30), LocalDate.now().plusDays(30)));

        UUID caregiverId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Shift shift = shiftRepo.save(new Shift(
            agency.getId(), null, client.getId(), caregiverId,
            serviceType.getId(), auth.getId(),
            now.minusMinutes(5), now.plusHours(4)));

        String token = loginAndGetToken();

        ClockInRequest clockInReq = new ClockInRequest(
            new BigDecimal("30.2672"), new BigDecimal("-97.7431"),
            VerificationMethod.GPS, false, null);
        restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/clock-in",
            HttpMethod.POST, new HttpEntity<>(clockInReq, authHeaders(token)),
            ShiftDetailResponse.class);

        ClockOutRequest clockOutReq = new ClockOutRequest(false, null);
        ResponseEntity<ShiftDetailResponse> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/clock-out",
            HttpMethod.POST, new HttpEntity<>(clockOutReq, authHeaders(token)),
            ShiftDetailResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Authorization updated = authorizationRepo.findById(auth.getId()).orElseThrow();
        assertThat(updated.getUsedUnits()).isEqualByComparingTo(new BigDecimal("1.00"));
    }
}
