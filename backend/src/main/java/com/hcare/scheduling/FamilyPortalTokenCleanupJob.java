package com.hcare.scheduling;

import com.hcare.domain.FamilyPortalTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Nightly cleanup of expired family portal invite tokens.
 * Tokens have a 72-hour TTL; this job prevents unbounded accumulation of stale rows.
 * Runs at 3 AM UTC daily — well after the ShiftGenerationScheduler (2 AM) to avoid
 * contention on the database during maintenance windows.
 */
@Component
public class FamilyPortalTokenCleanupJob {

    private final FamilyPortalTokenRepository tokenRepo;

    public FamilyPortalTokenCleanupJob(FamilyPortalTokenRepository tokenRepo) {
        this.tokenRepo = tokenRepo;
    }

    @Scheduled(cron = "${hcare.portal.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void deleteExpiredTokens() {
        tokenRepo.deleteExpired(LocalDateTime.now(ZoneOffset.UTC));
    }
}
