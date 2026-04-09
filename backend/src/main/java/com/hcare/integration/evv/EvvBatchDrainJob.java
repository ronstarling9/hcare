package com.hcare.integration.evv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hcare.evv.AggregatorType;
import com.hcare.integration.CredentialEncryptionService;
import com.hcare.integration.audit.IntegrationAuditWriter;
import com.hcare.multitenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nightly scheduled job that drains the EVV batch submission queue.
 *
 * <p>C9: Uses {@link EvvSubmissionRecordSystemRepository} (unfiltered) to find all agencies with
 * pending batch records. Processes each agency independently (C17: per-agency exception isolation).
 *
 * <p>C5/C13 two-phase per-record processing:
 * <ol>
 *   <li>Tx 1: {@code markInFlight()} — optimistic claim; returns 1 if claimed, 0 if already
 *       claimed by another node (M_new_9: skip if 0).
 *   <li>Submit outside any transaction (network I/O not inside DB tx).
 *   <li>Tx 2: {@code markFinal()} — write outcome + aggregatorVisitId + C11 dual-write to
 *       EvvRecord.
 * </ol>
 *
 * <p>C13: Per-aggregator credential cache ({@code Map<AggregatorType, Object>}) within a single
 * agency drain to avoid repeated decrypt calls.
 */
@Component
public class EvvBatchDrainJob {

    private static final Logger log = LoggerFactory.getLogger(EvvBatchDrainJob.class);

    private final EvvSubmissionRecordSystemRepository systemRepo;
    private final EvvBatchRecordManager batchRecordManager;
    private final AgencyEvvCredentialsRepository credentialsRepository;
    private final CredentialEncryptionService encryptionService;
    private final EvvStrategyFactory strategyFactory;
    private final EvvSubmissionContextAssembler contextAssembler;
    private final IntegrationAuditWriter auditWriter;
    private final ObjectMapper objectMapper;

    public EvvBatchDrainJob(
            EvvSubmissionRecordSystemRepository systemRepo,
            EvvBatchRecordManager batchRecordManager,
            AgencyEvvCredentialsRepository credentialsRepository,
            CredentialEncryptionService encryptionService,
            EvvStrategyFactory strategyFactory,
            EvvSubmissionContextAssembler contextAssembler,
            IntegrationAuditWriter auditWriter,
            ObjectMapper objectMapper) {
        this.systemRepo = systemRepo;
        this.batchRecordManager = batchRecordManager;
        this.credentialsRepository = credentialsRepository;
        this.encryptionService = encryptionService;
        this.strategyFactory = strategyFactory;
        this.contextAssembler = contextAssembler;
        this.auditWriter = auditWriter;
        this.objectMapper = objectMapper;
    }

    /**
     * Nightly drain at 2 AM UTC.
     *
     * <p>C9: Query all distinct agencies with PENDING BATCH records using the unfiltered
     * system repository. Process each agency in isolation (C17).
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void drain() {
        log.info("EvvBatchDrainJob starting");
        List<UUID> agencies = systemRepo.findDistinctAgenciesWithPendingBatch();
        log.info("EvvBatchDrainJob: {} agencies with pending batch records", agencies.size());

        for (UUID agencyId : agencies) {
            // C17: one failed agency must not abort others
            try {
                TenantContext.set(agencyId);
                drainForAgency(agencyId);
            } catch (Exception e) {
                log.error("EvvBatchDrainJob: unhandled exception for agencyId={} — continuing to next agency",
                        agencyId, e);
            } finally {
                TenantContext.clear();
            }
        }
        log.info("EvvBatchDrainJob completed");
    }

    /**
     * Drains all pending batch records for a single agency.
     *
     * <p>M_new_13: credential cache per aggregator type — decrypt once per aggregator per drain
     * cycle, not once per record.
     *
     * <p>M_new_9: if {@code markInFlight()} returns 0, the record was already claimed by another
     * node; skip and continue to the next record.
     *
     * <p>M_new_11: null-guard on {@code contextJson} — skip records with missing context and log.
     */
    private void drainForAgency(UUID agencyId) {
        // M_new_13: per-aggregator credential cache for this drain cycle
        Map<AggregatorType, Object> credentialCache = new HashMap<>();

        // Process records one at a time (two-phase claim + finalize)
        int processed = 0;
        while (true) {
            List<EvvSubmissionRecord> candidates = systemRepo
                    .findNextBatchPending(agencyId, PageRequest.of(0, 1));
            if (candidates.isEmpty()) {
                break;
            }
            EvvSubmissionRecord record = candidates.get(0);

            // Tx 1: claim the record (optimistic lock via status transition PENDING → IN_FLIGHT)
            int claimed = batchRecordManager.claimRecord(record.getId());
            if (claimed == 0) {
                // M_new_9: already claimed by another node — skip
                log.debug("EvvBatchDrainJob: record id={} already claimed by another node — skipping",
                        record.getId());
                continue;
            }

            // M_new_11: null-guard on contextJson
            if (record.getContextJson() == null || record.getContextJson().isBlank()) {
                log.error("EvvBatchDrainJob: null/empty contextJson for record id={} agencyId={} — marking REJECTED",
                        record.getId(), agencyId);
                batchRecordManager.finalizeRecord(record.getId(), EvvSubmissionStatus.REJECTED.name(),
                        null, null);
                processed++;
                continue;
            }

            // Deserialize context
            EvvSubmissionContext ctx;
            try {
                ctx = objectMapper.readValue(record.getContextJson(), EvvSubmissionContext.class);
            } catch (Exception e) {
                log.error("EvvBatchDrainJob: failed to deserialize contextJson for record id={} — marking REJECTED",
                        record.getId(), e);
                batchRecordManager.finalizeRecord(record.getId(), EvvSubmissionStatus.REJECTED.name(),
                        null, null);
                processed++;
                continue;
            }

            AggregatorType aggregatorType = AggregatorType.valueOf(record.getAggregatorType());

            // Resolve strategy and credentials (with cache)
            EvvSubmissionStrategy strategy;
            Object typedCreds;
            try {
                strategy = strategyFactory.strategyFor(aggregatorType);
                typedCreds = resolveCredentials(agencyId, aggregatorType, strategy, credentialCache);
            } catch (Exception e) {
                log.error("EvvBatchDrainJob: failed to resolve strategy/credentials for record id={} aggregator={} — marking REJECTED",
                        record.getId(), aggregatorType, e);
                batchRecordManager.finalizeRecord(record.getId(), EvvSubmissionStatus.REJECTED.name(),
                        null, null);
                auditWriter.record(agencyId, ctx.evvRecordId(), aggregatorType.name(),
                        "BATCH_SUBMIT", false, 0L, "CREDENTIAL_ERROR");
                processed++;
                continue;
            }

            // Submit outside any transaction (network I/O)
            long startMs = System.currentTimeMillis();
            EvvSubmissionResult result = strategy.submit(ctx, typedCreds);
            long durationMs = System.currentTimeMillis() - startMs;

            auditWriter.record(agencyId, ctx.evvRecordId(), aggregatorType.name(),
                    "BATCH_SUBMIT", result.success(), durationMs,
                    result.success() ? null : result.errorCode());

            // Tx 2: finalize — write outcome and C11 dual-write to EvvRecord in the SAME transaction
            String finalStatus = result.success()
                    ? EvvSubmissionStatus.SUBMITTED.name()
                    : EvvSubmissionStatus.REJECTED.name();
            UUID evvRecordIdForC11 = (result.success() && result.aggregatorVisitId() != null)
                    ? ctx.evvRecordId()
                    : null;
            batchRecordManager.finalizeRecord(record.getId(), finalStatus,
                    result.aggregatorVisitId(), evvRecordIdForC11);

            if (result.success()) {
                log.info("EvvBatchDrainJob: submitted record id={} aggregatorVisitId={} aggregator={}",
                        record.getId(), result.aggregatorVisitId(), aggregatorType);
            } else {
                log.error("EvvBatchDrainJob: submission failed for record id={} errorCode={} errorMessage={}",
                        record.getId(), result.errorCode(), result.errorMessage());
            }

            processed++;
        }

        log.info("EvvBatchDrainJob: processed {} records for agencyId={}", processed, agencyId);
    }

    /**
     * M_new_13: resolve and cache credentials per aggregator type within a drain cycle.
     */
    @SuppressWarnings("unchecked")
    private Object resolveCredentials(UUID agencyId, AggregatorType aggregatorType,
                                       EvvSubmissionStrategy strategy,
                                       Map<AggregatorType, Object> cache) {
        if (cache.containsKey(aggregatorType)) {
            return cache.get(aggregatorType);
        }

        AgencyEvvCredentials agencyCredentials = credentialsRepository
                .findByAgencyIdAndAggregatorTypeAndActiveTrue(agencyId, aggregatorType.name())
                .orElseThrow(() -> new IllegalStateException(
                        "No active credentials for agencyId=" + agencyId
                                + " aggregatorType=" + aggregatorType));

        Object typedCreds = encryptionService.decrypt(
                agencyCredentials.getCredentialsEncrypted(),
                strategy.credentialClass());

        cache.put(aggregatorType, typedCreds);
        return typedCreds;
    }

    /**
     * On application startup, reset any IN_FLIGHT records older than 10 minutes back to PENDING.
     * These are records that were claimed by a node that crashed before finalizing.
     *
     * <p>Cutoff: {@code LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10)}.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetStaleInFlightOnStartup() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10);
        int reset = systemRepo.resetStaleInFlight(cutoff);
        if (reset > 0) {
            log.info("EvvBatchDrainJob startup: reset {} stale IN_FLIGHT records (older than 10 min) to PENDING",
                    reset);
        }
    }
}
