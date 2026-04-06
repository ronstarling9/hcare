package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class CaregiverSubEntitiesIT extends AbstractIntegrationTest {

    @Autowired private AgencyRepository agencyRepo;
    @Autowired private CaregiverRepository caregiverRepo;
    @Autowired private CaregiverCredentialRepository credentialRepo;
    @Autowired private BackgroundCheckRepository bgCheckRepo;
    @Autowired private CaregiverAvailabilityRepository availabilityRepo;
    @Autowired private CaregiverScoringProfileRepository scoringProfileRepo;

    private Agency agency;
    private Caregiver caregiver;

    @BeforeEach
    void setup() {
        agency = agencyRepo.save(new Agency("Sub-Entity Agency", "TX"));
        caregiver = caregiverRepo.save(
            new Caregiver(agency.getId(), "Carol", "White", "carol@test.com"));
    }

    @Test
    void credential_can_be_saved_and_retrieved() {
        CaregiverCredential cred = credentialRepo.save(new CaregiverCredential(
            caregiver.getId(), agency.getId(), CredentialType.HHA,
            LocalDate.of(2023, 1, 15), LocalDate.of(2025, 1, 15)));

        CaregiverCredential loaded = credentialRepo.findById(cred.getId()).orElseThrow();
        assertThat(loaded.getCredentialType()).isEqualTo(CredentialType.HHA);
        assertThat(loaded.isVerified()).isFalse();
        assertThat(loaded.getCaregiverId()).isEqualTo(caregiver.getId());
        assertThat(loaded.getExpiryDate()).isEqualTo(LocalDate.of(2025, 1, 15));
    }

    @Test
    void background_check_result_is_persisted() {
        BackgroundCheck check = bgCheckRepo.save(new BackgroundCheck(
            caregiver.getId(), agency.getId(), BackgroundCheckType.OIG,
            BackgroundCheckResult.CLEAR, LocalDate.now()));

        BackgroundCheck loaded = bgCheckRepo.findById(check.getId()).orElseThrow();
        assertThat(loaded.getCheckType()).isEqualTo(BackgroundCheckType.OIG);
        assertThat(loaded.getResult()).isEqualTo(BackgroundCheckResult.CLEAR);
    }

    @Test
    void availability_stores_weekly_time_block() {
        CaregiverAvailability avail = availabilityRepo.save(new CaregiverAvailability(
            caregiver.getId(), agency.getId(),
            DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(16, 0)));

        CaregiverAvailability loaded = availabilityRepo.findById(avail.getId()).orElseThrow();
        assertThat(loaded.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(loaded.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(loaded.getEndTime()).isEqualTo(LocalTime.of(16, 0));
    }

    @Test
    void scoring_profile_initialises_with_zero_values() {
        CaregiverScoringProfile profile = scoringProfileRepo.save(
            new CaregiverScoringProfile(caregiver.getId(), agency.getId()));

        CaregiverScoringProfile loaded = scoringProfileRepo.findById(profile.getId()).orElseThrow();
        assertThat(loaded.getCancelRate()).isEqualByComparingTo("0");
        assertThat(loaded.getCurrentWeekHours()).isEqualByComparingTo("0");
        assertThat(loaded.getCaregiverId()).isEqualTo(caregiver.getId());
    }

    @Test
    void credential_verify_sets_verified_flag_and_verifier() {
        UUID adminId = UUID.randomUUID();
        CaregiverCredential cred = credentialRepo.save(new CaregiverCredential(
            caregiver.getId(), agency.getId(), CredentialType.CPR, null, null));

        cred.verify(adminId);
        credentialRepo.save(cred);

        CaregiverCredential loaded = credentialRepo.findById(cred.getId()).orElseThrow();
        assertThat(loaded.isVerified()).isTrue();
        assertThat(loaded.getVerifiedBy()).isEqualTo(adminId);
    }

    @Test
    void scoring_profile_updateAfterShiftCompletion_accumulates_hours() {
        CaregiverScoringProfile profile = scoringProfileRepo.save(
            new CaregiverScoringProfile(caregiver.getId(), agency.getId()));

        profile.updateAfterShiftCompletion(new java.math.BigDecimal("4.0"));
        scoringProfileRepo.save(profile);

        CaregiverScoringProfile loaded = scoringProfileRepo.findById(profile.getId()).orElseThrow();
        assertThat(loaded.getCurrentWeekHours()).isEqualByComparingTo("4.0");
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }
}
