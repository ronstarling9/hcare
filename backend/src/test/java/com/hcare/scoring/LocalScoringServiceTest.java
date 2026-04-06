package com.hcare.scoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hcare.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.*;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// LENIENT: many tests stub a full "success path" in @BeforeEach but only exercise one path.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocalScoringServiceTest {

    @Mock CaregiverRepository caregiverRepository;
    @Mock CaregiverAvailabilityRepository availabilityRepository;
    @Mock CaregiverCredentialRepository credentialRepository;
    @Mock ShiftRepository shiftRepository;
    @Mock CaregiverScoringProfileRepository scoringProfileRepository;
    @Mock CaregiverClientAffinityRepository affinityRepository;
    @Mock AuthorizationRepository authorizationRepository;
    @Mock FeatureFlagsRepository featureFlagsRepository;
    @Mock ClientRepository clientRepository;
    @Mock ServiceTypeRepository serviceTypeRepository;

    LocalScoringService service;

    static final UUID AGENCY_ID       = UUID.randomUUID();
    static final UUID CLIENT_ID       = UUID.randomUUID();
    static final UUID CAREGIVER_ID    = UUID.randomUUID();
    static final UUID SERVICE_TYPE_ID = UUID.randomUUID();

    // Monday 2026-04-20, 09:00–13:00 (4 hours)
    static final LocalDateTime SHIFT_START = LocalDateTime.of(2026, 4, 20, 9, 0);
    static final LocalDateTime SHIFT_END   = LocalDateTime.of(2026, 4, 20, 13, 0);

    // Austin, TX — client location
    static final BigDecimal CLIENT_LAT = new BigDecimal("30.2672");
    static final BigDecimal CLIENT_LNG = new BigDecimal("-97.7431");
    // ~0.3 miles north of client
    static final BigDecimal NEAR_LAT   = new BigDecimal("30.2700");
    static final BigDecimal NEAR_LNG   = new BigDecimal("-97.7400");

    @BeforeEach
    void setup() {
        service = new LocalScoringService(
            caregiverRepository, availabilityRepository, credentialRepository,
            shiftRepository, scoringProfileRepository, affinityRepository,
            authorizationRepository, featureFlagsRepository, clientRepository,
            serviceTypeRepository, new ObjectMapper()
        );
    }

    // ── fixture builders ──────────────────────────────────────────────────────

    private Caregiver buildActiveCaregiver() {
        Caregiver c = mock(Caregiver.class);
        when(c.getId()).thenReturn(CAREGIVER_ID);
        when(c.getStatus()).thenReturn(CaregiverStatus.ACTIVE);
        when(c.getHomeLat()).thenReturn(NEAR_LAT);
        when(c.getHomeLng()).thenReturn(NEAR_LNG);
        when(c.getLanguages()).thenReturn("[]");
        when(c.hasPet()).thenReturn(false);
        return c;
    }

    private Client buildClient() {
        Client cl = mock(Client.class);
        when(cl.getLat()).thenReturn(CLIENT_LAT);
        when(cl.getLng()).thenReturn(CLIENT_LNG);
        when(cl.getPreferredLanguages()).thenReturn("[]");
        when(cl.isNoPetCaregiver()).thenReturn(false);
        return cl;
    }

    private ServiceType buildServiceTypeNoCredentials() {
        ServiceType st = mock(ServiceType.class);
        when(st.getRequiredCredentials()).thenReturn("[]");
        return st;
    }

    private ShiftMatchRequest buildRequest() {
        return new ShiftMatchRequest(AGENCY_ID, CLIENT_ID, SERVICE_TYPE_ID, null,
            SHIFT_START, SHIFT_END);
    }

    /** Wires up all mocks so a single caregiver passes all hard filters (AI disabled). */
    private void setupSuccessPathMocks(Caregiver caregiver, Client client, ServiceType st) {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(st));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.empty()); // AI disabled
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(caregiver));

        CaregiverAvailability avail = new CaregiverAvailability(
            CAREGIVER_ID, AGENCY_ID, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0));
        when(availabilityRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(List.of(avail));
        when(shiftRepository.findOverlapping(
            eq(CAREGIVER_ID), any(), any())).thenReturn(Collections.emptyList());
        when(credentialRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(Collections.emptyList());
        when(scoringProfileRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(Optional.empty());
    }

    // ── hard filter tests ─────────────────────────────────────────────────────

    @Test
    void inactive_caregiver_is_excluded() {
        Client client = buildClient();
        ServiceType st = buildServiceTypeNoCredentials();
        Caregiver inactive = mock(Caregiver.class);
        when(inactive.getStatus()).thenReturn(CaregiverStatus.INACTIVE);
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(st));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.empty());
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(inactive));

        assertThat(service.rankCandidates(buildRequest())).isEmpty();
    }

    @Test
    void caregiver_with_no_availability_is_excluded() {
        Client client = buildClient();
        ServiceType st = buildServiceTypeNoCredentials();
        Caregiver caregiver = buildActiveCaregiver();
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(st));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.empty());
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(caregiver));
        when(availabilityRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(Collections.emptyList());

        assertThat(service.rankCandidates(buildRequest())).isEmpty();
    }

    @Test
    void caregiver_with_availability_on_wrong_day_is_excluded() {
        Client client = buildClient();
        ServiceType st = buildServiceTypeNoCredentials();
        Caregiver caregiver = buildActiveCaregiver();
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(st));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.empty());
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(caregiver));
        // Tuesday availability only — shift is Monday
        CaregiverAvailability tuesdayAvail = new CaregiverAvailability(
            CAREGIVER_ID, AGENCY_ID, DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(17, 0));
        when(availabilityRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(List.of(tuesdayAvail));

        assertThat(service.rankCandidates(buildRequest())).isEmpty();
    }

    @Test
    void caregiver_with_overlapping_shift_is_excluded() {
        Client client = buildClient();
        ServiceType st = buildServiceTypeNoCredentials();
        Caregiver caregiver = buildActiveCaregiver();
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(st));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.empty());
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(caregiver));
        when(availabilityRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(List.of(
            new CaregiverAvailability(CAREGIVER_ID, AGENCY_ID, DayOfWeek.MONDAY,
                LocalTime.of(8, 0), LocalTime.of(17, 0))));

        // Overlapping ASSIGNED shift 10:00–14:00 (overlaps our 09:00–13:00)
        Shift conflict = mock(Shift.class);
        when(conflict.getStatus()).thenReturn(ShiftStatus.ASSIGNED);
        when(conflict.getScheduledStart()).thenReturn(LocalDateTime.of(2026, 4, 20, 10, 0));
        when(conflict.getScheduledEnd()).thenReturn(LocalDateTime.of(2026, 4, 20, 14, 0));
        when(shiftRepository.findOverlapping(
            eq(CAREGIVER_ID), any(), any())).thenReturn(List.of(conflict));

        assertThat(service.rankCandidates(buildRequest())).isEmpty();
    }

    @Test
    void cancelled_shift_does_not_block_candidate() {
        Caregiver caregiver = buildActiveCaregiver();
        setupSuccessPathMocks(caregiver, buildClient(), buildServiceTypeNoCredentials());

        Shift cancelled = mock(Shift.class);
        when(cancelled.getStatus()).thenReturn(ShiftStatus.CANCELLED);
        when(cancelled.getScheduledStart()).thenReturn(LocalDateTime.of(2026, 4, 20, 10, 0));
        when(cancelled.getScheduledEnd()).thenReturn(LocalDateTime.of(2026, 4, 20, 14, 0));
        when(shiftRepository.findOverlapping(
            eq(CAREGIVER_ID), any(), any())).thenReturn(List.of(cancelled));

        assertThat(service.rankCandidates(buildRequest())).hasSize(1);
    }

    @Test
    void expired_required_credential_is_excluded() {
        Client client = buildClient();
        Caregiver caregiver = buildActiveCaregiver();
        ServiceType requiresHha = mock(ServiceType.class);
        when(requiresHha.getRequiredCredentials()).thenReturn("[\"HHA\"]");
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(requiresHha));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.empty());
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(caregiver));
        when(availabilityRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(List.of(
            new CaregiverAvailability(CAREGIVER_ID, AGENCY_ID, DayOfWeek.MONDAY,
                LocalTime.of(8, 0), LocalTime.of(17, 0))));
        when(shiftRepository.findOverlapping(
            eq(CAREGIVER_ID), any(), any())).thenReturn(Collections.emptyList());

        CaregiverCredential expired = new CaregiverCredential(
            CAREGIVER_ID, AGENCY_ID, CredentialType.HHA,
            LocalDate.of(2024, 1, 1), LocalDate.now().minusDays(1)); // expired yesterday
        expired.verify(UUID.randomUUID());
        when(credentialRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(List.of(expired));

        assertThat(service.rankCandidates(buildRequest())).isEmpty();
    }

    @Test
    void unverified_required_credential_is_excluded() {
        Client client = buildClient();
        Caregiver caregiver = buildActiveCaregiver();
        ServiceType requiresCna = mock(ServiceType.class);
        when(requiresCna.getRequiredCredentials()).thenReturn("[\"CNA\"]");
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(requiresCna));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.empty());
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(caregiver));
        when(availabilityRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(List.of(
            new CaregiverAvailability(CAREGIVER_ID, AGENCY_ID, DayOfWeek.MONDAY,
                LocalTime.of(8, 0), LocalTime.of(17, 0))));
        when(shiftRepository.findOverlapping(
            eq(CAREGIVER_ID), any(), any())).thenReturn(Collections.emptyList());

        // Valid expiry but NOT verified (verify() never called)
        CaregiverCredential unverified = new CaregiverCredential(
            CAREGIVER_ID, AGENCY_ID, CredentialType.CNA,
            LocalDate.of(2025, 1, 1), LocalDate.now().plusYears(1));
        when(credentialRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(List.of(unverified));

        assertThat(service.rankCandidates(buildRequest())).isEmpty();
    }

    @Test
    void exhausted_authorization_excludes_all_candidates() {
        Client client = buildClient();
        ServiceType st = buildServiceTypeNoCredentials();
        Caregiver caregiver = buildActiveCaregiver();
        UUID AUTH_ID = UUID.randomUUID();
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(st));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.empty());
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(caregiver));
        when(availabilityRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(List.of(
            new CaregiverAvailability(CAREGIVER_ID, AGENCY_ID, DayOfWeek.MONDAY,
                LocalTime.of(8, 0), LocalTime.of(17, 0))));
        when(shiftRepository.findOverlapping(
            eq(CAREGIVER_ID), any(), any())).thenReturn(Collections.emptyList());
        when(credentialRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(Collections.emptyList());

        Authorization exhausted = mock(Authorization.class);
        when(exhausted.getUsedUnits()).thenReturn(new BigDecimal("40.00"));
        when(exhausted.getAuthorizedUnits()).thenReturn(new BigDecimal("40.00")); // used == authorized
        when(authorizationRepository.findById(AUTH_ID)).thenReturn(Optional.of(exhausted));

        ShiftMatchRequest req = new ShiftMatchRequest(
            AGENCY_ID, CLIENT_ID, SERVICE_TYPE_ID, AUTH_ID, SHIFT_START, SHIFT_END);
        assertThat(service.rankCandidates(req)).isEmpty();
    }

    @Test
    void unknown_authorization_id_throws_illegal_argument() {
        Client client = buildClient();
        ServiceType st = buildServiceTypeNoCredentials();
        UUID unknownAuthId = UUID.randomUUID();
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(st));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.empty());
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(Collections.emptyList());
        when(authorizationRepository.findById(unknownAuthId)).thenReturn(Optional.empty());

        ShiftMatchRequest req = new ShiftMatchRequest(
            AGENCY_ID, CLIENT_ID, SERVICE_TYPE_ID, unknownAuthId, SHIFT_START, SHIFT_END);
        assertThatThrownBy(() -> service.rankCandidates(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(unknownAuthId.toString());
    }

    @Test
    void all_hard_filters_pass_returns_candidate_with_no_score_when_ai_disabled() {
        Caregiver caregiver = buildActiveCaregiver();
        setupSuccessPathMocks(caregiver, buildClient(), buildServiceTypeNoCredentials());

        List<RankedCaregiver> results = service.rankCandidates(buildRequest());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).caregiverId()).isEqualTo(CAREGIVER_ID);
        assertThat(results.get(0).score()).isEqualTo(0.0);
        assertThat(results.get(0).explanation()).isNull();
    }

    // ── scoring tests ─────────────────────────────────────────────────────────

    @Test
    void ai_enabled_returns_nonzero_score_and_explanation() {
        Client client = buildClient();
        ServiceType st = buildServiceTypeNoCredentials();
        Caregiver caregiver = buildActiveCaregiver();
        FeatureFlags flags = mock(FeatureFlags.class);
        when(flags.isAiSchedulingEnabled()).thenReturn(true);

        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(st));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.of(flags));
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(caregiver));
        when(availabilityRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(List.of(
            new CaregiverAvailability(CAREGIVER_ID, AGENCY_ID, DayOfWeek.MONDAY,
                LocalTime.of(8, 0), LocalTime.of(17, 0))));
        when(shiftRepository.findOverlapping(
            eq(CAREGIVER_ID), any(), any())).thenReturn(Collections.emptyList());
        when(credentialRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(Collections.emptyList());
        when(scoringProfileRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(Optional.empty());

        RankedCaregiver result = service.rankCandidates(buildRequest()).get(0);

        assertThat(result.score()).isGreaterThan(0.0);
        assertThat(result.explanation()).contains("miles away").contains("no overtime risk");
    }

    @Test
    void overtime_risk_lowers_score_vs_no_overtime() {
        Client client = buildClient();
        ServiceType st = buildServiceTypeNoCredentials();
        FeatureFlags flags = mock(FeatureFlags.class);
        when(flags.isAiSchedulingEnabled()).thenReturn(true);
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(st));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.of(flags));
        when(credentialRepository.findByCaregiverId(any())).thenReturn(Collections.emptyList());
        when(shiftRepository.findOverlapping(any(), any(), any()))
            .thenReturn(Collections.emptyList());

        // Caregiver A: 38h this week + 4h shift = 42h (OT risk)
        UUID CG_OT = UUID.randomUUID();
        Caregiver cgOt = mock(Caregiver.class);
        when(cgOt.getId()).thenReturn(CG_OT);
        when(cgOt.getStatus()).thenReturn(CaregiverStatus.ACTIVE);
        when(cgOt.getHomeLat()).thenReturn(NEAR_LAT);
        when(cgOt.getHomeLng()).thenReturn(NEAR_LNG);
        when(cgOt.getLanguages()).thenReturn("[]");
        when(cgOt.hasPet()).thenReturn(false);
        when(availabilityRepository.findByCaregiverId(CG_OT)).thenReturn(List.of(
            new CaregiverAvailability(CG_OT, AGENCY_ID, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0))));
        CaregiverScoringProfile otProfile = new CaregiverScoringProfile(CG_OT, AGENCY_ID);
        otProfile.updateAfterShiftCompletion(new BigDecimal("38.00"));
        when(scoringProfileRepository.findByCaregiverId(CG_OT)).thenReturn(Optional.of(otProfile));
        when(affinityRepository.findByScoringProfileIdAndClientId(any(), any())).thenReturn(Optional.empty());

        // Caregiver B: 0h this week (no OT risk)
        UUID CG_NO_OT = UUID.randomUUID();
        Caregiver cgNoOt = mock(Caregiver.class);
        when(cgNoOt.getId()).thenReturn(CG_NO_OT);
        when(cgNoOt.getStatus()).thenReturn(CaregiverStatus.ACTIVE);
        when(cgNoOt.getHomeLat()).thenReturn(NEAR_LAT);
        when(cgNoOt.getHomeLng()).thenReturn(NEAR_LNG);
        when(cgNoOt.getLanguages()).thenReturn("[]");
        when(cgNoOt.hasPet()).thenReturn(false);
        when(availabilityRepository.findByCaregiverId(CG_NO_OT)).thenReturn(List.of(
            new CaregiverAvailability(CG_NO_OT, AGENCY_ID, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0))));
        when(scoringProfileRepository.findByCaregiverId(CG_NO_OT)).thenReturn(Optional.empty());

        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(cgOt, cgNoOt));

        List<RankedCaregiver> results = service.rankCandidates(buildRequest());
        assertThat(results).hasSize(2);
        // No-OT caregiver ranks first (higher score)
        assertThat(results.get(0).caregiverId()).isEqualTo(CG_NO_OT);
        assertThat(results.get(0).explanation()).contains("no overtime risk");
        assertThat(results.get(1).explanation()).contains("overtime risk");
    }

    @Test
    void language_mismatch_reduces_score_vs_no_preference() {
        ServiceType st = buildServiceTypeNoCredentials();
        FeatureFlags flags = mock(FeatureFlags.class);
        when(flags.isAiSchedulingEnabled()).thenReturn(true);
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(st));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.of(flags));

        Caregiver englishOnly = mock(Caregiver.class);
        when(englishOnly.getId()).thenReturn(CAREGIVER_ID);
        when(englishOnly.getStatus()).thenReturn(CaregiverStatus.ACTIVE);
        when(englishOnly.getHomeLat()).thenReturn(NEAR_LAT);
        when(englishOnly.getHomeLng()).thenReturn(NEAR_LNG);
        when(englishOnly.getLanguages()).thenReturn("[\"en\"]");
        when(englishOnly.hasPet()).thenReturn(false);
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(englishOnly));
        when(availabilityRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(List.of(
            new CaregiverAvailability(CAREGIVER_ID, AGENCY_ID, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0))));
        when(shiftRepository.findOverlapping(eq(CAREGIVER_ID), any(), any()))
            .thenReturn(Collections.emptyList());
        when(credentialRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(Collections.emptyList());
        when(scoringProfileRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(Optional.empty());

        // Client wants Spanish
        Client spanishClient = mock(Client.class);
        when(spanishClient.getLat()).thenReturn(CLIENT_LAT);
        when(spanishClient.getLng()).thenReturn(CLIENT_LNG);
        when(spanishClient.getPreferredLanguages()).thenReturn("[\"es\"]");
        when(spanishClient.isNoPetCaregiver()).thenReturn(false);
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(spanishClient));
        double mismatchScore = service.rankCandidates(buildRequest()).get(0).score();

        // Client has no language preference
        Client noPreferenceClient = buildClient();
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(noPreferenceClient));
        double noPreferenceScore = service.rankCandidates(buildRequest()).get(0).score();

        assertThat(mismatchScore).isLessThan(noPreferenceScore);
    }

    @Test
    void pet_mismatch_reduces_score() {
        ServiceType st = buildServiceTypeNoCredentials();
        FeatureFlags flags = mock(FeatureFlags.class);
        when(flags.isAiSchedulingEnabled()).thenReturn(true);
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(st));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.of(flags));

        Caregiver cgWithPet = mock(Caregiver.class);
        when(cgWithPet.getId()).thenReturn(CAREGIVER_ID);
        when(cgWithPet.getStatus()).thenReturn(CaregiverStatus.ACTIVE);
        when(cgWithPet.getHomeLat()).thenReturn(NEAR_LAT);
        when(cgWithPet.getHomeLng()).thenReturn(NEAR_LNG);
        when(cgWithPet.getLanguages()).thenReturn("[]");
        when(cgWithPet.hasPet()).thenReturn(true);
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(cgWithPet));
        when(availabilityRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(List.of(
            new CaregiverAvailability(CAREGIVER_ID, AGENCY_ID, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0))));
        when(shiftRepository.findOverlapping(eq(CAREGIVER_ID), any(), any()))
            .thenReturn(Collections.emptyList());
        when(credentialRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(Collections.emptyList());
        when(scoringProfileRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(Optional.empty());

        Client noPetClient = mock(Client.class);
        when(noPetClient.getLat()).thenReturn(CLIENT_LAT);
        when(noPetClient.getLng()).thenReturn(CLIENT_LNG);
        when(noPetClient.getPreferredLanguages()).thenReturn("[]");
        when(noPetClient.isNoPetCaregiver()).thenReturn(true); // allergic
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(noPetClient));
        double petMismatchScore = service.rankCandidates(buildRequest()).get(0).score();

        Client noPreferenceClient = buildClient();
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(noPreferenceClient));
        double noPetPreferenceScore = service.rankCandidates(buildRequest()).get(0).score();

        assertThat(petMismatchScore).isLessThan(noPetPreferenceScore);
    }

    @Test
    void higher_continuity_increases_score() {
        Client client = buildClient();
        ServiceType st = buildServiceTypeNoCredentials();
        FeatureFlags flags = mock(FeatureFlags.class);
        when(flags.isAiSchedulingEnabled()).thenReturn(true);
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(st));
        when(featureFlagsRepository.findByAgencyId(AGENCY_ID)).thenReturn(Optional.of(flags));
        Caregiver caregiver = buildActiveCaregiver();
        when(caregiverRepository.findByAgencyId(AGENCY_ID)).thenReturn(List.of(caregiver));
        when(availabilityRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(List.of(
            new CaregiverAvailability(CAREGIVER_ID, AGENCY_ID, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0))));
        when(shiftRepository.findOverlapping(eq(CAREGIVER_ID), any(), any()))
            .thenReturn(Collections.emptyList());
        when(credentialRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(Collections.emptyList());

        CaregiverScoringProfile profile = new CaregiverScoringProfile(CAREGIVER_ID, AGENCY_ID);
        when(scoringProfileRepository.findByCaregiverId(CAREGIVER_ID)).thenReturn(Optional.of(profile));

        when(affinityRepository.findByScoringProfileIdAndClientId(any(), eq(CLIENT_ID)))
            .thenReturn(Optional.empty());
        double noVisitScore = service.rankCandidates(buildRequest()).get(0).score();

        CaregiverClientAffinity affinity = mock(CaregiverClientAffinity.class);
        when(affinity.getVisitCount()).thenReturn(5);
        when(affinityRepository.findByScoringProfileIdAndClientId(any(), eq(CLIENT_ID)))
            .thenReturn(Optional.of(affinity));
        double fiveVisitScore = service.rankCandidates(buildRequest()).get(0).score();

        assertThat(fiveVisitScore).isGreaterThan(noVisitScore);
    }

    // ── event listener tests ──────────────────────────────────────────────────

    @Test
    void onShiftCompleted_creates_profile_and_updates_hours() {
        UUID CG = UUID.randomUUID();
        UUID CL = UUID.randomUUID();
        UUID AG = UUID.randomUUID();
        LocalDateTime timeIn  = LocalDateTime.of(2026, 4, 20, 9, 0);
        LocalDateTime timeOut = LocalDateTime.of(2026, 4, 20, 13, 0); // 4h

        when(scoringProfileRepository.findByCaregiverId(CG)).thenReturn(Optional.empty());
        CaregiverScoringProfile newProfile = new CaregiverScoringProfile(CG, AG);
        when(scoringProfileRepository.save(any(CaregiverScoringProfile.class))).thenReturn(newProfile);
        // insertIfNotExists is a no-op in the unit test; findByScoringProfileIdAndClientId must
        // return non-empty afterward (orElseThrow) so supply the affinity directly.
        CaregiverClientAffinity newAffinity = new CaregiverClientAffinity(newProfile.getId(), CL, AG);
        doNothing().when(affinityRepository).insertIfNotExists(any(), any(), any());
        when(affinityRepository.findByScoringProfileIdAndClientId(any(), eq(CL)))
            .thenReturn(Optional.of(newAffinity));
        when(affinityRepository.save(any(CaregiverClientAffinity.class))).thenReturn(newAffinity);

        service.onShiftCompleted(new ShiftCompletedEvent(UUID.randomUUID(), CG, CL, AG, timeIn, timeOut));

        verify(scoringProfileRepository, atLeastOnce()).save(argThat(p ->
            p.getCurrentWeekHours().compareTo(new BigDecimal("4.00")) == 0
            && p.getTotalCompletedShifts() == 1));
    }

    @Test
    void onShiftCompleted_increments_affinity_visit_count() {
        UUID CG = UUID.randomUUID();
        UUID CL = UUID.randomUUID();
        UUID AG = UUID.randomUUID();
        UUID PROFILE_ID = UUID.randomUUID();

        CaregiverScoringProfile existingProfile = mock(CaregiverScoringProfile.class);
        when(existingProfile.getId()).thenReturn(PROFILE_ID);
        when(scoringProfileRepository.findByCaregiverId(CG)).thenReturn(Optional.of(existingProfile));
        when(scoringProfileRepository.save(any())).thenReturn(existingProfile);

        CaregiverClientAffinity affinity = new CaregiverClientAffinity(PROFILE_ID, CL, AG);
        when(affinityRepository.findByScoringProfileIdAndClientId(PROFILE_ID, CL))
            .thenReturn(Optional.of(affinity));
        when(affinityRepository.save(any())).thenReturn(affinity);
        doNothing().when(affinityRepository).insertIfNotExists(any(), any(), any());

        service.onShiftCompleted(new ShiftCompletedEvent(UUID.randomUUID(), CG, CL, AG,
            LocalDateTime.of(2026, 4, 20, 9, 0), LocalDateTime.of(2026, 4, 20, 13, 0)));

        assertThat(affinity.getVisitCount()).isEqualTo(1);
    }

    @Test
    void onShiftCancelled_increments_cancel_count_and_recalculates_rate() {
        UUID CG = UUID.randomUUID();
        UUID AG = UUID.randomUUID();
        CaregiverScoringProfile profile = new CaregiverScoringProfile(CG, AG);
        when(scoringProfileRepository.findByCaregiverId(CG)).thenReturn(Optional.of(profile));
        when(scoringProfileRepository.save(any())).thenReturn(profile);

        service.onShiftCancelled(new ShiftCancelledEvent(UUID.randomUUID(), CG, AG));

        assertThat(profile.getTotalCancelledShifts()).isEqualTo(1);
        assertThat(profile.getCancelRate()).isEqualByComparingTo("1.0000");
    }

    @Test
    void onShiftCompleted_null_caregiverId_is_no_op() {
        service.onShiftCompleted(new ShiftCompletedEvent(
            UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(),
            LocalDateTime.now(), LocalDateTime.now().plusHours(4)));
        verify(scoringProfileRepository, never()).findByCaregiverId(any());
    }
}
