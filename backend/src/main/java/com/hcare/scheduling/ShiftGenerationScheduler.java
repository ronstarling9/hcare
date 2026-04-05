package com.hcare.scheduling;

import com.hcare.domain.RecurrencePattern;
import com.hcare.domain.RecurrencePatternRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Nightly job that advances the shift-generation frontier for all active RecurrencePatterns
 * that have not yet been generated through the 8-week horizon.
 *
 * The @Scheduled cron is controlled by hcare.scheduling.shift-generation-cron, which defaults
 * to "0 0 2 * * *" (2 AM daily) and is set to "-" in application-test.yml to prevent the
 * cron from firing during integration tests. Tests invoke advanceGenerationFrontier() directly.
 */
@Component
public class ShiftGenerationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ShiftGenerationScheduler.class);

    private final RecurrencePatternRepository patternRepository;
    private final ShiftGenerationService shiftGenerationService;

    public ShiftGenerationScheduler(RecurrencePatternRepository patternRepository,
                                     ShiftGenerationService shiftGenerationService) {
        this.patternRepository = patternRepository;
        this.shiftGenerationService = shiftGenerationService;
    }

    @Scheduled(cron = "${hcare.scheduling.shift-generation-cron:0 0 2 * * *}")
    public void advanceGenerationFrontier() {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusWeeks(LocalShiftGenerationService.HORIZON_WEEKS);
        List<RecurrencePattern> patterns = patternRepository.findActivePatternsBehindHorizon(horizon, today);
        for (RecurrencePattern pattern : patterns) {
            try {
                shiftGenerationService.generateForPattern(pattern);
            } catch (Exception e) {
                // Log and continue — one failed pattern must not block all others.
                // ObjectOptimisticLockingFailureException is the expected concurrent-edit case;
                // the pattern will be retried on the next nightly run.
                log.error("Failed to generate shifts for pattern {} (agency {}): {}",
                    pattern.getId(), pattern.getAgencyId(), e.getMessage());
            }
        }
    }
}
