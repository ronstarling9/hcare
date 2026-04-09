package com.hcare.api.v1.family;

import com.hcare.AbstractIntegrationTest;
import com.hcare.api.v1.auth.dto.LoginRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import com.hcare.api.v1.family.dto.InviteResponse;
import com.hcare.api.v1.family.dto.PortalDashboardResponse;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(statements = {
    "TRUNCATE TABLE evv_records, shifts, family_portal_tokens, family_portal_users, " +
    "service_types, caregivers, clients, agency_users, agencies RESTART IDENTITY CASCADE"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class FamilyPortalDashboardControllerIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private AgencyRepository agencyRepo;
    @Autowired private AgencyUserRepository userRepo;
    @Autowired private ClientRepository clientRepo;
    @Autowired private CaregiverRepository caregiverRepo;
    @Autowired private ServiceTypeRepository serviceTypeRepo;
    @Autowired private ShiftRepository shiftRepo;
    @Autowired private FamilyPortalUserRepository fpuRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID clientId;
    private UUID agencyId;
    private UUID caregiverId;
    private UUID serviceTypeId;
    private String adminToken;

    @BeforeEach
    void seed() {
        Agency agency = agencyRepo.save(new Agency("Dashboard IT Agency", "NY"));
        agencyId = agency.getId();
        userRepo.save(new AgencyUser(agencyId, "admin@dashit.com",
            passwordEncoder.encode("Pass1234!"), UserRole.ADMIN));
        Client client = clientRepo.save(
            new Client(agencyId, "Margaret", "Test", LocalDate.of(1940, 1, 1)));
        clientId = client.getId();
        ServiceType st = serviceTypeRepo.save(
            new ServiceType(agencyId, "Personal Care Aide", "PCA", false, "[]"));
        serviceTypeId = st.getId();
        Caregiver cg = caregiverRepo.save(
            new Caregiver(agencyId, "Maria", "Gonzalez", "maria@example.com"));
        caregiverId = cg.getId();
        adminToken = restTemplate.postForEntity("/api/v1/auth/login",
            new LoginRequest("admin@dashit.com", "Pass1234!"), LoginResponse.class)
            .getBody().token();
    }

    private String obtainPortalJwt() {
        // Invite + verify to get a FAMILY_PORTAL JWT
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(adminToken);
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<InviteResponse> inviteResp = restTemplate.exchange(
            "/api/v1/clients/" + clientId + "/family-portal-users/invite",
            HttpMethod.POST,
            new HttpEntity<>("{\"email\":\"family@test.com\"}", h),
            InviteResponse.class);
        String rawToken = UriComponentsBuilder
            .fromUriString(inviteResp.getBody().inviteUrl())
            .build()
            .getQueryParams()
            .getFirst("token");
        PortalVerifyResponse verifyResp = restTemplate.postForEntity(
            "/api/v1/family/auth/verify",
            new PortalVerifyRequest(rawToken),
            PortalVerifyResponse.class).getBody();
        return verifyResp.jwt();
    }

    private HttpHeaders portalAuth(String jwt) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwt);
        return h;
    }

    @Test
    void dashboard_noShiftsToday_returnsTodayVisitNull() {
        String jwt = obtainPortalJwt();
        ResponseEntity<PortalDashboardResponse> resp = restTemplate.exchange(
            "/api/v1/family/portal/dashboard", HttpMethod.GET,
            new HttpEntity<>(portalAuth(jwt)), PortalDashboardResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().clientFirstName()).isEqualTo("Margaret");
        assertThat(resp.getBody().todayVisit()).isNull();
        assertThat(resp.getBody().upcomingVisits()).isEmpty();
        assertThat(resp.getBody().lastVisit()).isNull();
    }

    @Test
    void dashboard_adminJwt_returns403() {
        HttpHeaders adminH = new HttpHeaders();
        adminH.setBearerAuth(adminToken);
        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/family/portal/dashboard", HttpMethod.GET,
            new HttpEntity<>(adminH), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void dashboard_afterUserRemoved_returns403WithRevocationCode() {
        String jwt = obtainPortalJwt();
        // Remove the FamilyPortalUser
        FamilyPortalUser fpu = fpuRepo.findByClientId(clientId).get(0);
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(adminToken);
        restTemplate.exchange(
            "/api/v1/clients/" + clientId + "/family-portal-users/" + fpu.getId(),
            HttpMethod.DELETE, new HttpEntity<>(h), Void.class);

        // Dashboard with still-valid JWT should return 403
        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/family/portal/dashboard", HttpMethod.GET,
            new HttpEntity<>(portalAuth(jwt)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody()).contains("PORTAL_ACCESS_REVOKED");
    }

    @Test
    void dashboard_dischargedClient_returns410() {
        String jwt = obtainPortalJwt();
        // Discharge the client
        Client client = clientRepo.findById(clientId).get();
        client.setStatus(ClientStatus.DISCHARGED);
        clientRepo.save(client);

        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/family/portal/dashboard", HttpMethod.GET,
            new HttpEntity<>(portalAuth(jwt)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(resp.getBody()).contains("CLIENT_DISCHARGED");
    }

    @Test
    void dashboard_withAssignedShift_returnsGreyStatus() {
        // Seed a shift for today
        LocalDateTime today = LocalDateTime.now(ZoneOffset.UTC).withHour(9).withMinute(0).withSecond(0).withNano(0);
        shiftRepo.save(new Shift(agencyId, null, clientId, caregiverId, serviceTypeId, null,
            today, today.plusHours(2)));

        String jwt = obtainPortalJwt();
        ResponseEntity<PortalDashboardResponse> resp = restTemplate.exchange(
            "/api/v1/family/portal/dashboard", HttpMethod.GET,
            new HttpEntity<>(portalAuth(jwt)), PortalDashboardResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().todayVisit()).isNotNull();
        assertThat(resp.getBody().todayVisit().status()).isEqualTo("GREY");
        // Caregiver card present for non-CANCELLED shift
        assertThat(resp.getBody().todayVisit().caregiver()).isNotNull();
        assertThat(resp.getBody().todayVisit().caregiver().name()).isEqualTo("Maria Gonzalez");
    }

    @Test
    void dashboard_cancelledShift_caregiverCardIsNull() {
        LocalDateTime today = LocalDateTime.now(ZoneOffset.UTC).withHour(9).withMinute(0).withSecond(0).withNano(0);
        Shift shift = shiftRepo.save(new Shift(agencyId, null, clientId, caregiverId, serviceTypeId, null,
            today, today.plusHours(2)));
        shift.setStatus(ShiftStatus.CANCELLED);
        shiftRepo.save(shift);

        String jwt = obtainPortalJwt();
        ResponseEntity<PortalDashboardResponse> resp = restTemplate.exchange(
            "/api/v1/family/portal/dashboard", HttpMethod.GET,
            new HttpEntity<>(portalAuth(jwt)), PortalDashboardResponse.class);
        // CANCELLED shift is excluded from todayVisit (only non-cancelled are shown)
        assertThat(resp.getBody().todayVisit()).isNull();
    }
}
