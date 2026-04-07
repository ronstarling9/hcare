package com.hcare.scoring;

import com.hcare.AbstractIntegrationTest;
import com.hcare.domain.*;
import com.hcare.multitenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocalScoringServiceIT extends AbstractIntegrationTest {

    @Autowired LocalScoringService scoringService;
    @Autowired AgencyRepository agencyRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired CaregiverRepository caregiverRepository;
    @Autowired ServiceTypeRepository serviceTypeRepository;
    @Autowired FeatureFlagsRepository featureFlagsRepository;
    @Autowired CaregiverAvailabilityRepository availabilityRepository;
    @Autowired CaregiverScoringProfileRepository scoringProfileRepository;
    @Autowired CaregiverClientAffinityRepository affinityRepository;
    @Autowired TransactionTemplate transactionTemplate;

    Agency agency;
    Client client;
    ServiceType serviceType;
    LocalDateTime shiftStart;
    LocalDateTime shiftEnd;

    @BeforeEach
    void setupData() {
        agency = agencyRepository.save(new Agency("Scoring IT Agency", "TX"));
        TenantContext.set(agency.getId());

        client = clientRepository.save(new Client(
            agency.getId(), "Score", "Client", java.time.LocalDate.of(1950, 1, 1)));
        client.setLat(new BigDecimal("30.2672"));
        client.setLng(new BigDecimal("-97.7431"));
        clientRepository.save(client);

        serviceType = serviceTypeRepository.save(
            new ServiceType(agency.getId(), "PCS", "PCS-SCORE-IT", true, "[]"));

        FeatureFlags flags = new FeatureFlags(agency.getId());
        flags.setAiSchedulingEnabled(true);
        featureFlagsRepository.save(flags);

        // Next Monday at 09:00–13:00
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        shiftStart = nextMonday.atTime(9, 0);
        shiftEnd   = nextMonday.atTime(13, 0);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private Caregiver createCaregiverWithAvailability(String name, BigDecimal homeLat, BigDecimal homeLng) {
        Caregiver c = new Caregiver(agency.getId(), name, "CG", name.toLowerCase() + "@test.com");
        c.setHomeLat(homeLat);
        c.setHomeLng(homeLng);
        Caregiver saved = caregiverRepository.save(c);
        availabilityRepository.save(new CaregiverAvailability(
            saved.getId(), agency.getId(), DayOfWeek.MONDAY,
            LocalTime.of(8, 0), LocalTime.of(17, 0)));
        return saved;
    }

    @Test
    void near_caregiver_ranks_above_far_caregiver() {
        // Near: ~0.3 miles from client
        Caregiver near = createCaregiverWithAvailability(
            "Near", new BigDecimal("30.2700"), new BigDecimal("-97.7400"));
        // Far: ~16 miles from client
        Caregiver far = createCaregiverWithAvailability(
            "Far", new BigDecimal("30.5000"), new BigDecimal("-97.7431"));

        ShiftMatchRequest request = new ShiftMatchRequest(
            agency.getId(), client.getId(), serviceType.getId(), null, shiftStart, shiftEnd);

        List<RankedCaregiver> results = transactionTemplate.execute(status ->
            scoringService.rankCandidates(request));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).caregiverId()).isEqualTo(near.getId());
        assertThat(results.get(0).score()).isGreaterThan(results.get(1).score());
        assertThat(results.get(0).explanation())
            .contains("miles away")
            .contains("no overtime risk");
    }

    @Test
    void caregiver_without_availability_is_excluded_from_results() {
        Caregiver eligible = createCaregiverWithAvailability(
            "Eligible", new BigDecimal("30.2700"), new BigDecimal("-97.7400"));
        // Second caregiver has no availability windows
        caregiverRepository.save(new Caregiver(
            agency.getId(), "NoAvail", "CG", "noavail@test.com"));

        ShiftMatchRequest request = new ShiftMatchRequest(
            agency.getId(), client.getId(), serviceType.getId(), null, shiftStart, shiftEnd);

        List<RankedCaregiver> results = transactionTemplate.execute(status ->
            scoringService.rankCandidates(request));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).caregiverId()).isEqualTo(eligible.getId());
    }

    @Test
    void onShiftCompleted_creates_profile_and_affinity() {
        Caregiver caregiver = createCaregiverWithAvailability(
            "EventCG", new BigDecimal("30.2700"), new BigDecimal("-97.7400"));

        // Call listener directly — @Transactional(REQUIRES_NEW) creates its own transaction
        scoringService.onShiftCompleted(new ShiftCompletedEvent(
            UUID.randomUUID(),
            caregiver.getId(), client.getId(), agency.getId(),
            LocalDateTime.of(2026, 4, 20, 9, 0),
            LocalDateTime.of(2026, 4, 20, 13, 0) // 4 hours
        ));

        CaregiverScoringProfile profile = transactionTemplate.execute(status ->
            scoringProfileRepository.findByCaregiverId(caregiver.getId()).orElse(null));
        assertThat(profile).isNotNull();
        assertThat(profile.getCurrentWeekHours()).isEqualByComparingTo("4.00");
        assertThat(profile.getTotalCompletedShifts()).isEqualTo(1);

        List<CaregiverClientAffinity> affinities = transactionTemplate.execute(status ->
            affinityRepository.findByScoringProfileId(profile.getId()));
        assertThat(affinities).hasSize(1);
        assertThat(affinities.get(0).getClientId()).isEqualTo(client.getId());
        assertThat(affinities.get(0).getVisitCount()).isEqualTo(1);
    }

    @Test
    void repeated_onShiftCompleted_accumulates_continuity_and_hours() {
        Caregiver caregiver = createCaregiverWithAvailability(
            "ContinuousCG", new BigDecimal("30.2700"), new BigDecimal("-97.7400"));

        for (int i = 0; i < 3; i++) {
            final int day = 20 + i;
            scoringService.onShiftCompleted(new ShiftCompletedEvent(
                UUID.randomUUID(), caregiver.getId(), client.getId(), agency.getId(),
                LocalDateTime.of(2026, 4, day, 9, 0),
                LocalDateTime.of(2026, 4, day, 13, 0)));
        }

        CaregiverScoringProfile profile = transactionTemplate.execute(status ->
            scoringProfileRepository.findByCaregiverId(caregiver.getId()).orElseThrow());
        assertThat(profile.getTotalCompletedShifts()).isEqualTo(3);
        assertThat(profile.getCurrentWeekHours()).isEqualByComparingTo("12.00");

        List<CaregiverClientAffinity> affinities = transactionTemplate.execute(status ->
            affinityRepository.findByScoringProfileId(profile.getId()));
        assertThat(affinities.get(0).getVisitCount()).isEqualTo(3);
    }

    @Test
    void continuity_improves_ranking_after_visits_recorded() {
        Caregiver cgWithHistory = createCaregiverWithAvailability(
            "WithHistory", new BigDecimal("30.2700"), new BigDecimal("-97.7400"));
        Caregiver cgNoHistory = createCaregiverWithAvailability(
            "NoHistory", new BigDecimal("30.2700"), new BigDecimal("-97.7400"));
        // Both near the client — only continuity differentiates them

        // Record 5 visits for cgWithHistory
        for (int i = 0; i < 5; i++) {
            final int day = 20 + i;
            scoringService.onShiftCompleted(new ShiftCompletedEvent(
                UUID.randomUUID(), cgWithHistory.getId(), client.getId(), agency.getId(),
                LocalDateTime.of(2026, 4, day, 9, 0),
                LocalDateTime.of(2026, 4, day, 13, 0)));
        }

        ShiftMatchRequest request = new ShiftMatchRequest(
            agency.getId(), client.getId(), serviceType.getId(), null, shiftStart, shiftEnd);

        List<RankedCaregiver> results = transactionTemplate.execute(status ->
            scoringService.rankCandidates(request));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).caregiverId()).isEqualTo(cgWithHistory.getId());
        assertThat(results.get(0).explanation()).contains("worked with this client 5 times");
    }

    @Test
    void resetWeeklyHours_zeroes_current_week_hours_for_all_profiles() {
        Caregiver cg1 = createCaregiverWithAvailability(
            "Reset1", new BigDecimal("30.2700"), new BigDecimal("-97.7400"));
        Caregiver cg2 = createCaregiverWithAvailability(
            "Reset2", new BigDecimal("30.2700"), new BigDecimal("-97.7400"));

        // Record hours for both caregivers
        scoringService.onShiftCompleted(new ShiftCompletedEvent(
            UUID.randomUUID(), cg1.getId(), client.getId(), agency.getId(),
            LocalDateTime.of(2026, 4, 20, 9, 0), LocalDateTime.of(2026, 4, 20, 13, 0)));
        scoringService.onShiftCompleted(new ShiftCompletedEvent(
            UUID.randomUUID(), cg2.getId(), client.getId(), agency.getId(),
            LocalDateTime.of(2026, 4, 20, 9, 0), LocalDateTime.of(2026, 4, 20, 17, 0)));

        // Verify hours accumulated before reset
        transactionTemplate.execute(status -> {
            assertThat(scoringProfileRepository.findByCaregiverId(cg1.getId())
                .orElseThrow().getCurrentWeekHours()).isEqualByComparingTo("4.00");
            assertThat(scoringProfileRepository.findByCaregiverId(cg2.getId())
                .orElseThrow().getCurrentWeekHours()).isEqualByComparingTo("8.00");
            return null;
        });

        // Invoke the scheduled method directly (cron is disabled in test profile)
        scoringService.resetWeeklyHours();

        transactionTemplate.execute(status -> {
            assertThat(scoringProfileRepository.findByCaregiverId(cg1.getId())
                .orElseThrow().getCurrentWeekHours()).isEqualByComparingTo("0.00");
            assertThat(scoringProfileRepository.findByCaregiverId(cg2.getId())
                .orElseThrow().getCurrentWeekHours()).isEqualByComparingTo("0.00");
            return null;
        });
    }

    @Test
    void cancelShift_increments_cancel_count_on_scoring_profile() {
        // seed: agency, caregiver, scoring profile with cancelCount = 0
        Caregiver cg = caregiverRepository.save(new Caregiver(agency.getId(), "Jane", "Smith", "jane@test.com"));
        CaregiverScoringProfile profile = scoringProfileRepository.save(new CaregiverScoringProfile(cg.getId(), agency.getId()));
        assertThat(profile.getTotalCancelledShifts()).isEqualTo(0);

        // publish the event directly (same as what ShiftSchedulingService does)
        ShiftCancelledEvent event = new ShiftCancelledEvent(UUID.randomUUID(), cg.getId(), agency.getId());
        scoringService.onShiftCancelled(event);

        CaregiverScoringProfile updated = scoringProfileRepository.findByCaregiverId(cg.getId()).orElseThrow();
        assertThat(updated.getTotalCancelledShifts()).isEqualTo(1);
    }
}
