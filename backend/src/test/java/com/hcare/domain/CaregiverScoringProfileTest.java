package com.hcare.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class CaregiverScoringProfileTest {

    private final CaregiverScoringProfile profile =
        new CaregiverScoringProfile(UUID.randomUUID(), UUID.randomUUID());

    @Test
    void updateAfterShiftCompletion_adds_hours_and_increments_completed_count() {
        profile.updateAfterShiftCompletion(new BigDecimal("4.00"));
        assertThat(profile.getCurrentWeekHours()).isEqualByComparingTo("4.00");
        assertThat(profile.getTotalCompletedShifts()).isEqualTo(1);
        assertThat(profile.getCancelRate()).isEqualByComparingTo("0.0000");
    }

    @Test
    void updateAfterShiftCancellation_increments_cancelled_count_and_recalculates_rate() {
        profile.updateAfterShiftCompletion(new BigDecimal("4.00")); // 1 completed
        profile.updateAfterShiftCancellation();                      // 1 cancelled
        assertThat(profile.getTotalCancelledShifts()).isEqualTo(1);
        // rate = 1 / (1 + 1) = 0.5000
        assertThat(profile.getCancelRate()).isEqualByComparingTo("0.5000");
    }

    @Test
    void cancel_rate_is_zero_when_no_shifts_recorded() {
        assertThat(profile.getCancelRate()).isEqualByComparingTo("0.0000");
    }

    @Test
    void cancel_rate_is_one_when_all_shifts_cancelled() {
        profile.updateAfterShiftCancellation();
        assertThat(profile.getCancelRate()).isEqualByComparingTo("1.0000");
    }

    @Test
    void resetWeeklyHours_sets_current_week_hours_to_zero() {
        profile.updateAfterShiftCompletion(new BigDecimal("38.00"));
        profile.resetWeeklyHours();
        assertThat(profile.getCurrentWeekHours()).isEqualByComparingTo("0.00");
    }

    @Test
    void multiple_completions_accumulate_hours_and_count() {
        profile.updateAfterShiftCompletion(new BigDecimal("4.00"));
        profile.updateAfterShiftCompletion(new BigDecimal("6.00"));
        assertThat(profile.getCurrentWeekHours()).isEqualByComparingTo("10.00");
        assertThat(profile.getTotalCompletedShifts()).isEqualTo(2);
    }
}
