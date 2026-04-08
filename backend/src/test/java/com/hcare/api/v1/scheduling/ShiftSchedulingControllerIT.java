package com.hcare.api.v1.scheduling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hcare.AbstractIntegrationTest;
import com.hcare.api.v1.auth.dto.LoginRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import com.hcare.api.v1.scheduling.dto.AssignCaregiverRequest;
import com.hcare.api.v1.visits.dto.ShiftDetailResponse;
import com.hcare.api.v1.scheduling.dto.CancelShiftRequest;
import com.hcare.api.v1.scheduling.dto.CreateShiftRequest;
import com.hcare.api.v1.scheduling.dto.RankedCaregiverResponse;
import com.hcare.api.v1.scheduling.dto.RespondToOfferRequest;
import com.hcare.api.v1.scheduling.dto.ShiftOfferSummary;
import com.hcare.api.v1.scheduling.dto.ShiftSummaryResponse;
import com.hcare.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(statements = {
    "TRUNCATE TABLE shift_offers, shifts, recurrence_patterns, authorizations, caregiver_scoring_profiles, " +
    "caregiver_client_affinities, caregivers, service_types, clients, agency_users, agencies RESTART IDENTITY CASCADE"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ShiftSchedulingControllerIT extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "correcthorsebatterystaple";

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AgencyRepository agencyRepo;
    @Autowired private AgencyUserRepository userRepo;
    @Autowired private ClientRepository clientRepo;
    @Autowired private ServiceTypeRepository serviceTypeRepo;
    @Autowired private ShiftRepository shiftRepo;
    @Autowired private CaregiverRepository caregiverRepo;
    @Autowired private ShiftOfferRepository shiftOfferRepo;
    @Autowired private AuthorizationRepository authorizationRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    private Agency agency;
    private Client client;
    private ServiceType serviceType;
    private Caregiver caregiver;

    @BeforeEach
    void seed() {
        agency = agencyRepo.save(new Agency("Sched Test Agency", "TX"));
        userRepo.save(new AgencyUser(agency.getId(), "sched-admin@test.com",
            passwordEncoder.encode(TEST_PASSWORD), UserRole.ADMIN));
        client = clientRepo.save(new Client(agency.getId(), "Cal", "Client", LocalDate.of(1970, 1, 1)));
        serviceType = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-IT", true, "[]"));
        caregiver = caregiverRepo.save(new Caregiver(agency.getId(), "Test", "Caregiver", "cg@test.com"));
    }

    private String token() {
        LoginRequest req = new LoginRequest("sched-admin@test.com", TEST_PASSWORD);
        return restTemplate.postForEntity("/api/v1/auth/login", req, LoginResponse.class)
            .getBody().token();
    }

    private HttpHeaders auth() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token());
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    // --- GET /shifts ---

    @Test
    void listShifts_returns_shifts_in_window_for_agency() {
        shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null,
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0)));

        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
            "/api/v1/shifts?start=2026-05-01T00:00:00&end=2026-05-08T00:00:00",
            HttpMethod.GET, new HttpEntity<>(auth()),
            new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) resp.getBody().get("content");
        assertThat(content).hasSize(1);
        Map<?, ?> first = (Map<?, ?>) content.get(0);
        assertThat(first.get("clientId")).isEqualTo(client.getId().toString());
    }

    @Test
    void listShifts_returns_401_without_token() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/shifts?start=2026-05-01T00:00:00&end=2026-05-08T00:00:00",
            HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listShifts_does_not_return_shifts_from_another_agency() {
        Agency other = agencyRepo.save(new Agency("Other Agency", "TX"));
        Client otherClient = clientRepo.save(new Client(other.getId(), "Other", "Client", LocalDate.of(1970, 1, 1)));
        ServiceType otherSt = serviceTypeRepo.save(new ServiceType(other.getId(), "PCS", "PCS-OTH", true, "[]"));
        shiftRepo.save(new Shift(other.getId(), null, otherClient.getId(), null, otherSt.getId(), null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0)));

        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
            "/api/v1/shifts?start=2026-05-01T00:00:00&end=2026-05-08T00:00:00",
            HttpMethod.GET, new HttpEntity<>(auth()),
            new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) resp.getBody().get("content");
        assertThat(content).isEmpty();
    }

    @Test
    void listShifts_withStatusFilter_returnsOnlyOpenShifts() {
        // Create one OPEN and one ASSIGNED shift
        shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null,
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0)));
        Shift assigned = new Shift(agency.getId(), null, client.getId(), caregiver.getId(),
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 4, 9, 0), LocalDateTime.of(2026, 5, 4, 13, 0));
        shiftRepo.save(assigned);

        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
            "/api/v1/shifts?start=2026-05-01T00:00:00&end=2026-05-08T00:00:00&status=OPEN",
            HttpMethod.GET, new HttpEntity<>(auth()),
            new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) resp.getBody().get("content");
        assertThat(content).hasSize(1);
        Map<?, ?> first = (Map<?, ?>) content.get(0);
        assertThat(first.get("status")).isEqualTo("OPEN");
    }

    @Test
    void listShifts_withInvalidStatus_returns400() {
        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/shifts?start=2026-05-01T00:00:00&end=2026-05-08T00:00:00&status=NOT_A_STATUS",
            HttpMethod.GET, new HttpEntity<>(auth()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- POST /shifts ---

    @Test
    void createShift_creates_open_shift_and_returns_201() {
        CreateShiftRequest req = new CreateShiftRequest(client.getId(), null, serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 10, 9, 0), LocalDateTime.of(2026, 5, 10, 13, 0), null);

        ResponseEntity<ShiftSummaryResponse> resp = restTemplate.exchange(
            "/api/v1/shifts", HttpMethod.POST,
            new HttpEntity<>(req, auth()), ShiftSummaryResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().status()).isEqualTo(ShiftStatus.OPEN);
        assertThat(resp.getBody().id()).isNotNull();
    }

    @Test
    void createShift_with_caregiverId_creates_assigned_shift() {
        CreateShiftRequest req = new CreateShiftRequest(client.getId(), caregiver.getId(),
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 11, 9, 0), LocalDateTime.of(2026, 5, 11, 13, 0), null);

        ResponseEntity<ShiftSummaryResponse> resp = restTemplate.exchange(
            "/api/v1/shifts", HttpMethod.POST,
            new HttpEntity<>(req, auth()), ShiftSummaryResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().status()).isEqualTo(ShiftStatus.ASSIGNED);
        assertThat(resp.getBody().caregiverId()).isEqualTo(caregiver.getId());
    }

    // --- PATCH /shifts/{id}/assign ---

    @Test
    void assignCaregiver_transitions_open_shift_to_assigned() {
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null,
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 12, 9, 0), LocalDateTime.of(2026, 5, 12, 13, 0)));

        ResponseEntity<ShiftSummaryResponse> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/assign",
            HttpMethod.PATCH,
            new HttpEntity<>(new AssignCaregiverRequest(caregiver.getId()), auth()),
            ShiftSummaryResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().status()).isEqualTo(ShiftStatus.ASSIGNED);
        assertThat(resp.getBody().caregiverId()).isEqualTo(caregiver.getId());
    }

    @Test
    void assignCaregiver_on_already_assigned_shift_returns_409() {
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), caregiver.getId(),
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 13, 9, 0), LocalDateTime.of(2026, 5, 13, 13, 0)));

        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/assign",
            HttpMethod.PATCH,
            new HttpEntity<>(new AssignCaregiverRequest(UUID.randomUUID()), auth()),
            String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // --- PATCH /shifts/{id}/unassign ---

    @Test
    void unassignCaregiver_transitions_assigned_shift_to_open() {
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), caregiver.getId(),
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 14, 9, 0), LocalDateTime.of(2026, 5, 14, 13, 0)));

        ResponseEntity<ShiftSummaryResponse> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/unassign",
            HttpMethod.PATCH,
            new HttpEntity<>(auth()),
            ShiftSummaryResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().status()).isEqualTo(ShiftStatus.OPEN);
        assertThat(resp.getBody().caregiverId()).isNull();
    }

    // --- PATCH /shifts/{id}/cancel ---

    @Test
    void cancelShift_transitions_open_shift_to_cancelled() {
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null,
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 15, 9, 0), LocalDateTime.of(2026, 5, 15, 13, 0)));

        ResponseEntity<ShiftSummaryResponse> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/cancel",
            HttpMethod.PATCH,
            new HttpEntity<>(new CancelShiftRequest("No longer needed"), auth()),
            ShiftSummaryResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().status()).isEqualTo(ShiftStatus.CANCELLED);
    }

    @Test
    void cancelShift_on_completed_shift_returns_409() {
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), caregiver.getId(),
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 16, 9, 0), LocalDateTime.of(2026, 5, 16, 13, 0)));
        shift.setStatus(ShiftStatus.COMPLETED);
        shiftRepo.save(shift);

        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/cancel",
            HttpMethod.PATCH,
            new HttpEntity<>(new CancelShiftRequest(null), auth()),
            String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // --- GET /shifts/{id}/candidates ---

    @Test
    void getCandidates_returns_200_with_list() {
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null,
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 17, 9, 0), LocalDateTime.of(2026, 5, 17, 13, 0)));

        ResponseEntity<List<RankedCaregiverResponse>> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/candidates",
            HttpMethod.GET, new HttpEntity<>(auth()),
            new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
    }

    // --- POST /shifts/{id}/broadcast ---

    @Test
    void broadcastShift_on_non_open_shift_returns_409() {
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), caregiver.getId(),
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 18, 9, 0), LocalDateTime.of(2026, 5, 18, 13, 0)));

        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/broadcast",
            HttpMethod.POST, new HttpEntity<>(auth()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void broadcastShift_on_open_shift_returns_200_with_offer_list() {
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null,
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 18, 9, 0), LocalDateTime.of(2026, 5, 18, 13, 0)));

        ResponseEntity<List<ShiftOfferSummary>> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/broadcast",
            HttpMethod.POST, new HttpEntity<>(auth()),
            new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
    }

    // --- GET /shifts/{id}/offers ---

    @Test
    void listOffers_returns_all_offers_for_shift() {
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null,
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 19, 9, 0), LocalDateTime.of(2026, 5, 19, 13, 0)));
        shiftOfferRepo.save(new ShiftOffer(shift.getId(), caregiver.getId(), agency.getId()));

        ResponseEntity<List<ShiftOfferSummary>> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/offers",
            HttpMethod.GET, new HttpEntity<>(auth()),
            new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
        assertThat(resp.getBody().get(0).caregiverId()).isEqualTo(caregiver.getId());
        assertThat(resp.getBody().get(0).response()).isEqualTo(ShiftOfferResponse.NO_RESPONSE);
    }

    // --- POST /shifts/{id}/offers/{offerId}/respond ---

    @Test
    void respondToOffer_accepted_assigns_caregiver_and_declines_others() {
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null,
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 20, 9, 0), LocalDateTime.of(2026, 5, 20, 13, 0)));
        Caregiver cg2 = caregiverRepo.save(new Caregiver(agency.getId(), "Other", "CG", "cg2@test.com"));
        ShiftOffer offerA = shiftOfferRepo.save(new ShiftOffer(shift.getId(), caregiver.getId(), agency.getId()));
        ShiftOffer offerB = shiftOfferRepo.save(new ShiftOffer(shift.getId(), cg2.getId(), agency.getId()));

        RespondToOfferRequest req = new RespondToOfferRequest(ShiftOfferResponse.ACCEPTED);
        ResponseEntity<ShiftOfferSummary> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/offers/" + offerA.getId() + "/respond",
            HttpMethod.POST, new HttpEntity<>(req, auth()), ShiftOfferSummary.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().response()).isEqualTo(ShiftOfferResponse.ACCEPTED);

        Shift updated = shiftRepo.findById(shift.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ShiftStatus.ASSIGNED);
        assertThat(updated.getCaregiverId()).isEqualTo(caregiver.getId());

        ShiftOffer declined = shiftOfferRepo.findById(offerB.getId()).orElseThrow();
        assertThat(declined.getResponse()).isEqualTo(ShiftOfferResponse.DECLINED);
    }

    @Test
    void respondToOffer_declined_does_not_change_shift_status() {
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null,
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 21, 9, 0), LocalDateTime.of(2026, 5, 21, 13, 0)));
        ShiftOffer offer = shiftOfferRepo.save(new ShiftOffer(shift.getId(), caregiver.getId(), agency.getId()));

        RespondToOfferRequest req = new RespondToOfferRequest(ShiftOfferResponse.DECLINED);
        ResponseEntity<ShiftOfferSummary> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/offers/" + offer.getId() + "/respond",
            HttpMethod.POST, new HttpEntity<>(req, auth()), ShiftOfferSummary.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().response()).isEqualTo(ShiftOfferResponse.DECLINED);

        Shift unchanged = shiftRepo.findById(shift.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(ShiftStatus.OPEN);
    }

    @Test
    void full_broadcast_and_accept_flow_assigns_caregiver_and_closes_other_offers() {
        // 1. Create two caregivers
        Caregiver cg1 = caregiverRepo.save(new Caregiver(agency.getId(), "First", "CG", "cg1-e2e@test.com"));
        Caregiver cg2 = caregiverRepo.save(new Caregiver(agency.getId(), "Second", "CG", "cg2-e2e@test.com"));

        // 2. Create an OPEN shift via POST /shifts
        CreateShiftRequest createReq = new CreateShiftRequest(
            client.getId(), null, serviceType.getId(), null,
            LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 1, 13, 0), null);
        ResponseEntity<ShiftSummaryResponse> createResp = restTemplate.exchange(
            "/api/v1/shifts", HttpMethod.POST,
            new HttpEntity<>(createReq, auth()), ShiftSummaryResponse.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID shiftId = createResp.getBody().id();

        // 3. Manually create offers for both caregivers
        ShiftOffer offer1 = shiftOfferRepo.save(new ShiftOffer(shiftId, cg1.getId(), agency.getId()));
        ShiftOffer offer2 = shiftOfferRepo.save(new ShiftOffer(shiftId, cg2.getId(), agency.getId()));

        // 4. GET /shifts/{id}/offers — expect 2 offers, both NO_RESPONSE
        ResponseEntity<List<ShiftOfferSummary>> offersResp = restTemplate.exchange(
            "/api/v1/shifts/" + shiftId + "/offers",
            HttpMethod.GET, new HttpEntity<>(auth()),
            new ParameterizedTypeReference<>() {});
        assertThat(offersResp.getBody()).hasSize(2)
            .allMatch(o -> o.response() == ShiftOfferResponse.NO_RESPONSE);

        // 5. cg1 ACCEPTS
        RespondToOfferRequest acceptReq = new RespondToOfferRequest(ShiftOfferResponse.ACCEPTED);
        ResponseEntity<ShiftOfferSummary> acceptResp = restTemplate.exchange(
            "/api/v1/shifts/" + shiftId + "/offers/" + offer1.getId() + "/respond",
            HttpMethod.POST, new HttpEntity<>(acceptReq, auth()), ShiftOfferSummary.class);
        assertThat(acceptResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(acceptResp.getBody().response()).isEqualTo(ShiftOfferResponse.ACCEPTED);

        // 6. Shift must now be ASSIGNED to cg1 — served by VisitController (GET /api/v1/shifts/{id})
        ResponseEntity<ShiftDetailResponse> shiftCheck = restTemplate.exchange(
            "/api/v1/shifts/" + shiftId,
            HttpMethod.GET, new HttpEntity<>(auth()), ShiftDetailResponse.class);
        assertThat(shiftCheck.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(shiftCheck.getBody().status()).isEqualTo(ShiftStatus.ASSIGNED);
        assertThat(shiftCheck.getBody().caregiverId()).isEqualTo(cg1.getId());

        // 7. offer2 must be auto-DECLINED
        ShiftOffer offer2Updated = shiftOfferRepo.findById(offer2.getId()).orElseThrow();
        assertThat(offer2Updated.getResponse()).isEqualTo(ShiftOfferResponse.DECLINED);

        // 8. A second ACCEPT attempt on offer2 must fail with 409 (already declined)
        ResponseEntity<String> secondAccept = restTemplate.exchange(
            "/api/v1/shifts/" + shiftId + "/offers/" + offer2.getId() + "/respond",
            HttpMethod.POST,
            new HttpEntity<>(new RespondToOfferRequest(ShiftOfferResponse.ACCEPTED), auth()),
            String.class);
        assertThat(secondAccept.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
