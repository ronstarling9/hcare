package com.hcare.api.v1.scheduling;

import com.hcare.AbstractIntegrationTest;
import com.hcare.api.v1.auth.dto.LoginRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import com.hcare.api.v1.scheduling.dto.CreateRecurrencePatternRequest;
import com.hcare.api.v1.scheduling.dto.RecurrencePatternResponse;
import com.hcare.api.v1.scheduling.dto.UpdateRecurrencePatternRequest;
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
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(statements = {
    "TRUNCATE TABLE shift_offers, shifts, recurrence_patterns, authorizations, caregiver_scoring_profiles, " +
    "caregiver_client_affinities, caregivers, service_types, clients, agency_users, agencies RESTART IDENTITY CASCADE"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class RecurrencePatternControllerIT extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "correcthorsebatterystaple";

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private AgencyRepository agencyRepo;
    @Autowired private AgencyUserRepository userRepo;
    @Autowired private ClientRepository clientRepo;
    @Autowired private ServiceTypeRepository serviceTypeRepo;
    @Autowired private ShiftRepository shiftRepo;
    @Autowired private RecurrencePatternRepository patternRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    private Agency agency;
    private Client client;
    private ServiceType serviceType;

    @BeforeEach
    void seed() {
        agency = agencyRepo.save(new Agency("Pattern Test Agency", "TX"));
        userRepo.save(new AgencyUser(agency.getId(), "pat-admin@test.com",
            passwordEncoder.encode(TEST_PASSWORD), UserRole.ADMIN));
        client = clientRepo.save(new Client(agency.getId(), "Pat", "Client", LocalDate.of(1970, 1, 1)));
        serviceType = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-PAT", true, "[]"));
    }

    private String token() {
        LoginRequest req = new LoginRequest("pat-admin@test.com", TEST_PASSWORD);
        return restTemplate.postForEntity("/api/v1/auth/login", req, LoginResponse.class)
            .getBody().token();
    }

    private HttpHeaders auth() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token());
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    // --- GET /recurrence-patterns ---

    @Test
    void listPatterns_returns_all_patterns_for_agency() {
        patternRepo.save(new RecurrencePattern(
            agency.getId(), client.getId(), serviceType.getId(),
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4)));
        patternRepo.save(new RecurrencePattern(
            agency.getId(), client.getId(), serviceType.getId(),
            LocalTime.of(14, 0), 60, "[\"WEDNESDAY\"]", LocalDate.of(2026, 5, 6)));

        ResponseEntity<List<RecurrencePatternResponse>> resp = restTemplate.exchange(
            "/api/v1/recurrence-patterns",
            HttpMethod.GET, new HttpEntity<>(auth()),
            new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(2);
    }

    // --- POST /recurrence-patterns ---

    @Test
    void createPattern_returns_201_and_generates_shifts() {
        CreateRecurrencePatternRequest req = new CreateRecurrencePatternRequest(
            client.getId(), null, serviceType.getId(), null,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]",
            LocalDate.of(2026, 5, 4), null);

        ResponseEntity<RecurrencePatternResponse> resp = restTemplate.exchange(
            "/api/v1/recurrence-patterns", HttpMethod.POST,
            new HttpEntity<>(req, auth()), RecurrencePatternResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().id()).isNotNull();
        assertThat(resp.getBody().active()).isTrue();
        assertThat(resp.getBody().scheduledStartTime()).isEqualTo(LocalTime.of(9, 0));

        UUID patternId = resp.getBody().id();
        assertThat(shiftRepo.findByClientIdAndScheduledStartBetween(
            client.getId(), LocalDate.of(2026, 5, 4).atStartOfDay(),
            LocalDate.of(2026, 5, 4).plusWeeks(9).atStartOfDay()))
            .isNotEmpty()
            .allMatch(s -> s.getSourcePatternId().equals(patternId));
    }

    @Test
    void createPattern_without_token_returns_401() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        CreateRecurrencePatternRequest req = new CreateRecurrencePatternRequest(
            client.getId(), null, serviceType.getId(), null,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]",
            LocalDate.of(2026, 5, 4), null);

        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/recurrence-patterns", HttpMethod.POST,
            new HttpEntity<>(req, h), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- GET /recurrence-patterns/{id} ---

    @Test
    void getPattern_returns_pattern_when_exists() {
        RecurrencePattern pattern = patternRepo.save(new RecurrencePattern(
            agency.getId(), client.getId(), serviceType.getId(),
            LocalTime.of(10, 0), 60, "[\"TUESDAY\"]", LocalDate.of(2026, 5, 5)));

        ResponseEntity<RecurrencePatternResponse> resp = restTemplate.exchange(
            "/api/v1/recurrence-patterns/" + pattern.getId(),
            HttpMethod.GET, new HttpEntity<>(auth()), RecurrencePatternResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().id()).isEqualTo(pattern.getId());
        assertThat(resp.getBody().scheduledDurationMinutes()).isEqualTo(60);
    }

    @Test
    void getPattern_returns_404_for_unknown_id() {
        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/recurrence-patterns/" + UUID.randomUUID(),
            HttpMethod.GET, new HttpEntity<>(auth()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- PATCH /recurrence-patterns/{id} ---

    @Test
    void updatePattern_scheduling_fields_trigger_regeneration() {
        RecurrencePattern pattern = patternRepo.save(new RecurrencePattern(
            agency.getId(), client.getId(), serviceType.getId(),
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4)));

        shiftRepo.save(new Shift(agency.getId(), pattern.getId(), client.getId(), null,
            serviceType.getId(), null,
            LocalDate.of(2026, 5, 11).atTime(9, 0),
            LocalDate.of(2026, 5, 11).atTime(11, 0)));

        UpdateRecurrencePatternRequest req = new UpdateRecurrencePatternRequest(
            LocalTime.of(14, 0), null, null, null, null, null);

        ResponseEntity<RecurrencePatternResponse> resp = restTemplate.exchange(
            "/api/v1/recurrence-patterns/" + pattern.getId(),
            HttpMethod.PATCH, new HttpEntity<>(req, auth()), RecurrencePatternResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().scheduledStartTime()).isEqualTo(LocalTime.of(14, 0));

        assertThat(shiftRepo.findByClientIdAndScheduledStartBetween(
            client.getId(), LocalDate.of(2026, 5, 11).atStartOfDay(),
            LocalDate.of(2026, 5, 11).plusDays(1).atStartOfDay()))
            .allMatch(s -> s.getScheduledStart().getHour() == 14);
    }

    @Test
    void updatePattern_non_scheduling_fields_do_not_trigger_regeneration() {
        RecurrencePattern pattern = patternRepo.save(new RecurrencePattern(
            agency.getId(), client.getId(), serviceType.getId(),
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4)));

        Shift existing = shiftRepo.save(new Shift(agency.getId(), pattern.getId(), client.getId(), null,
            serviceType.getId(), null,
            LocalDate.of(2026, 5, 11).atTime(9, 0),
            LocalDate.of(2026, 5, 11).atTime(11, 0)));

        UpdateRecurrencePatternRequest req = new UpdateRecurrencePatternRequest(
            null, null, null, null, null, LocalDate.of(2026, 12, 31));

        ResponseEntity<RecurrencePatternResponse> resp = restTemplate.exchange(
            "/api/v1/recurrence-patterns/" + pattern.getId(),
            HttpMethod.PATCH, new HttpEntity<>(req, auth()), RecurrencePatternResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().endDate()).isEqualTo(LocalDate.of(2026, 12, 31));

        assertThat(shiftRepo.findById(existing.getId())).isPresent();
    }

    // --- DELETE /recurrence-patterns/{id} ---

    @Test
    void deletePattern_deactivates_and_returns_204() {
        RecurrencePattern pattern = patternRepo.save(new RecurrencePattern(
            agency.getId(), client.getId(), serviceType.getId(),
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4)));

        ResponseEntity<Void> resp = restTemplate.exchange(
            "/api/v1/recurrence-patterns/" + pattern.getId(),
            HttpMethod.DELETE, new HttpEntity<>(auth()), Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        RecurrencePattern deactivated = patternRepo.findById(pattern.getId()).orElseThrow();
        assertThat(deactivated.isActive()).isFalse();
    }

    @Test
    void deletePattern_removes_future_unstarted_shifts() {
        RecurrencePattern pattern = patternRepo.save(new RecurrencePattern(
            agency.getId(), client.getId(), serviceType.getId(),
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4)));

        shiftRepo.save(new Shift(agency.getId(), pattern.getId(), client.getId(), null,
            serviceType.getId(), null,
            LocalDate.of(2026, 6, 1).atTime(9, 0),
            LocalDate.of(2026, 6, 1).atTime(11, 0)));

        restTemplate.exchange(
            "/api/v1/recurrence-patterns/" + pattern.getId(),
            HttpMethod.DELETE, new HttpEntity<>(auth()), Void.class);

        assertThat(shiftRepo.findByClientIdAndScheduledStartBetween(
            client.getId(), LocalDate.of(2026, 5, 31).atStartOfDay(),
            LocalDate.of(2026, 6, 2).atStartOfDay()))
            .isEmpty();
    }
}
