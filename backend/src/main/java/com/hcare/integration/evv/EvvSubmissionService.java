package com.hcare.integration.evv;

import com.hcare.domain.Authorization;
import com.hcare.domain.AuthorizationRepository;
import com.hcare.domain.Client;
import com.hcare.domain.ClientRepository;
import com.hcare.domain.EvvRecord;
import com.hcare.domain.EvvRecordRepository;
import com.hcare.domain.Payer;
import com.hcare.domain.PayerRepository;
import com.hcare.domain.Shift;
import com.hcare.domain.ShiftCompletedEvent;
import com.hcare.domain.ShiftRepository;
import com.hcare.evv.AggregatorType;
import com.hcare.evv.EvvSystemModel;
import com.hcare.integration.CredentialEncryptionService;
import com.hcare.integration.audit.IntegrationAuditWriter;
import com.hcare.integration.evv.exceptions.MissingCredentialsException;
import com.hcare.multitenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Core event listener that processes {@link ShiftCompletedEvent} and submits EVV records to the
 * appropriate aggregator.
 *
 * <p>C1 + C2: {@code @TransactionalEventListener} fires after the outer transaction commits on a
 * different thread. The {@link TenantContext} (ThreadLocal) has already been cleared by
 * {@code TenantFilterInterceptor.afterCompletion()}. We must re-bind TenantContext before ANY
 * repository call, and always clear it in a {@code finally} block.
 *
 * <p>Routing: {@link com.hcare.evv.PayerEvvRoutingConfig} (payer-specific) is checked first,
 * then falls back to {@link com.hcare.evv.EvvStateConfig#getDefaultAggregator()}. If the state
 * model is {@link EvvSystemModel#CLOSED}, we return immediately without submitting.
 *
 * <p>C11 dual-write: after a successful real-time submission, both
 * {@link EvvSubmissionRecord#setAggregatorVisitId} AND {@link EvvRecord#setAggregatorVisitId}
 * are updated in the same {@code REQUIRES_NEW} transaction.
 */
@Service
public class EvvSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(EvvSubmissionService.class);

    private final EvvRecordRepository evvRecordRepository;
    private final ShiftRepository shiftRepository;
    private final AuthorizationRepository authorizationRepository;
    private final ClientRepository clientRepository;
    private final PayerRepository payerRepository;
    private final EvvSubmissionRecordRepository submissionRecordRepository;
    private final EvvSubmissionContextAssembler contextAssembler;
    private final AgencyEvvCredentialsRepository credentialsRepository;
    private final CredentialEncryptionService encryptionService;
    private final EvvStrategyFactory strategyFactory;
    private final EvvBatchQueue batchQueue;
    private final EvvDeadLetterQueue deadLetterQueue;
    private final IntegrationAuditWriter auditWriter;

    public EvvSubmissionService(
            EvvRecordRepository evvRecordRepository,
            ShiftRepository shiftRepository,
            AuthorizationRepository authorizationRepository,
            ClientRepository clientRepository,
            PayerRepository payerRepository,
            EvvSubmissionRecordRepository submissionRecordRepository,
            EvvSubmissionContextAssembler contextAssembler,
            AgencyEvvCredentialsRepository credentialsRepository,
            CredentialEncryptionService encryptionService,
            EvvStrategyFactory strategyFactory,
            EvvBatchQueue batchQueue,
            EvvDeadLetterQueue deadLetterQueue,
            IntegrationAuditWriter auditWriter) {
        this.evvRecordRepository = evvRecordRepository;
        this.shiftRepository = shiftRepository;
        this.authorizationRepository = authorizationRepository;
        this.clientRepository = clientRepository;
        this.payerRepository = payerRepository;
        this.submissionRecordRepository = submissionRecordRepository;
        this.contextAssembler = contextAssembler;
        this.credentialsRepository = credentialsRepository;
        this.encryptionService = encryptionService;
        this.strategyFactory = strategyFactory;
        this.batchQueue = batchQueue;
        this.deadLetterQueue = deadLetterQueue;
        this.auditWriter = auditWriter;
    }

    /**
     * Processes a shift completion event by submitting the EVV record to the appropriate aggregator.
     *
     * <p>C1: TenantContext is re-bound from the event payload before any repository call, and
     * cleared in a {@code finally} block to prevent cross-tenant leakage on virtual threads.
     */
    @TransactionalEventListener
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onShiftCompleted(ShiftCompletedEvent event) {
        // C1: re-bind TenantContext — the outer request thread has already cleared it.
        TenantContext.set(event.agencyId());
        try {
            processShiftCompleted(event);
        } catch (Exception e) {
            log.error("Unhandled exception during EVV submission for shiftId={} agencyId={}",
                    event.shiftId(), event.agencyId(), e);
        } finally {
            // C1: always clear to prevent leakage across virtual thread reuse.
            TenantContext.clear();
        }
    }

    private void processShiftCompleted(ShiftCompletedEvent event) {
        // Step 1: find or validate the EVV record for this shift
        EvvRecord evvRecord = evvRecordRepository.findByShiftId(event.shiftId())
                .orElse(null);
        if (evvRecord == null) {
            log.warn("No EvvRecord found for shiftId={} — skipping EVV submission", event.shiftId());
            return;
        }

        // Step 2: skip if already submitted (idempotency guard)
        boolean alreadySubmitted = submissionRecordRepository.findByEvvRecordId(evvRecord.getId())
                .map(r -> EvvSubmissionStatus.SUBMITTED.name().equals(r.getStatus())
                        || EvvSubmissionStatus.ACCEPTED.name().equals(r.getStatus()))
                .orElse(false);
        if (alreadySubmitted) {
            log.info("EVV record already submitted for evvRecordId={} — skipping", evvRecord.getId());
            return;
        }

        // Step 3: resolve routing (payer + state)
        Shift shift = shiftRepository.findById(event.shiftId()).orElse(null);
        if (shift == null) {
            log.error("Shift not found for shiftId={} — cannot route EVV submission", event.shiftId());
            return;
        }

        UUID payerId = resolvePayerId(shift);
        Payer payer = payerId != null ? payerRepository.findById(payerId).orElse(null) : null;

        String stateCode = resolveStateCode(shift, payer);
        if (stateCode == null) {
            log.warn("Cannot determine stateCode for shiftId={} — skipping EVV submission",
                    event.shiftId());
            return;
        }

        // Step 4: check if CLOSED model — agency uses state system directly, no submission needed
        EvvSystemModel systemModel = contextAssembler.resolveSystemModel(stateCode);
        if (EvvSystemModel.CLOSED == systemModel) {
            log.info("State {} uses CLOSED EVV model — no aggregator submission needed for shiftId={}",
                    stateCode, event.shiftId());
            return;
        }

        // Step 5: resolve aggregator type
        AggregatorType aggregatorType = contextAssembler.resolveAggregatorType(stateCode, payer);
        if (aggregatorType == null) {
            log.error("Could not resolve aggregatorType for stateCode={} shiftId={} — skipping",
                    stateCode, event.shiftId());
            return;
        }

        // Additional guard: CLOSED aggregator type means same as CLOSED model
        if (AggregatorType.CLOSED == aggregatorType) {
            log.info("AggregatorType=CLOSED for stateCode={} — no submission for shiftId={}",
                    stateCode, event.shiftId());
            return;
        }

        // Step 6: assemble credential-free context (C16)
        EvvSubmissionContext ctx = contextAssembler.assemble(evvRecord.getId(), aggregatorType);

        // Step 7: look up strategy to determine real-time vs batch
        EvvSubmissionStrategy strategy = strategyFactory.strategyFor(aggregatorType);

        if (strategy.isRealTime()) {
            submitRealTime(ctx, strategy, evvRecord, event.agencyId());
        } else {
            // Step 8 (batch path): enqueue for nightly drain job
            batchQueue.enqueue(ctx, aggregatorType);
            log.info("Enqueued EVV record for batch submission: evvRecordId={} aggregator={}",
                    evvRecord.getId(), aggregatorType);
        }
    }

    /**
     * Performs real-time EVV submission and writes both audit log and C11 dual-write.
     */
    private void submitRealTime(EvvSubmissionContext ctx, EvvSubmissionStrategy strategy,
                                 EvvRecord evvRecord, UUID agencyId) {
        // Step 7a: look up and decrypt credentials
        AgencyEvvCredentials agencyCredentials = credentialsRepository
                .findByAgencyIdAndAggregatorTypeAndActiveTrue(
                        agencyId, ctx.aggregatorType().name())
                .orElse(null);

        if (agencyCredentials == null) {
            log.error("No active credentials found for agencyId={} aggregatorType={} — evvRecordId={}",
                    agencyId, ctx.aggregatorType(), ctx.evvRecordId());
            deadLetterQueue.enqueue(ctx, EvvSubmissionResult.failure(
                    "MISSING_CREDENTIALS",
                    "No active credentials for aggregator " + ctx.aggregatorType()));
            auditWriter.record(agencyId, ctx.evvRecordId(), ctx.aggregatorType().name(),
                    "SUBMIT", false, 0L, "MISSING_CREDENTIALS");
            return;
        }

        Object typedCreds;
        try {
            typedCreds = encryptionService.decrypt(
                    agencyCredentials.getCredentialsEncrypted(),
                    strategy.credentialClass());
        } catch (Exception e) {
            log.error("Credential decryption failed for agencyId={} aggregatorType={} — evvRecordId={}",
                    agencyId, ctx.aggregatorType(), ctx.evvRecordId(), e);
            deadLetterQueue.enqueue(ctx, EvvSubmissionResult.failure(
                    "CREDENTIAL_DECRYPT_FAILED", e.getMessage()));
            auditWriter.record(agencyId, ctx.evvRecordId(), ctx.aggregatorType().name(),
                    "SUBMIT", false, 0L, "CREDENTIAL_DECRYPT_FAILED");
            return;
        }

        // Step 7b: submit
        long startMs = System.currentTimeMillis();
        EvvSubmissionResult result = strategy.submit(ctx, typedCreds);
        long durationMs = System.currentTimeMillis() - startMs;

        auditWriter.record(agencyId, ctx.evvRecordId(), ctx.aggregatorType().name(),
                "SUBMIT", result.success(), durationMs,
                result.success() ? null : result.errorCode());

        if (result.success()) {
            // C11 dual-write: update BOTH EvvSubmissionRecord AND EvvRecord in this REQUIRES_NEW tx
            upsertSubmissionRecord(ctx, agencyId, EvvSubmissionStatus.SUBMITTED, result.aggregatorVisitId());
            evvRecord.setAggregatorVisitId(result.aggregatorVisitId());
            evvRecordRepository.save(evvRecord);
            log.info("EVV submission successful: evvRecordId={} aggregatorVisitId={} aggregator={}",
                    ctx.evvRecordId(), result.aggregatorVisitId(), ctx.aggregatorType());
        } else {
            log.error("EVV submission failed: evvRecordId={} errorCode={} errorMessage={}",
                    ctx.evvRecordId(), result.errorCode(), result.errorMessage());
            upsertSubmissionRecord(ctx, agencyId, EvvSubmissionStatus.REJECTED, null);
            deadLetterQueue.enqueue(ctx, result);
        }
    }

    /**
     * Creates or updates an {@link EvvSubmissionRecord} for the given context.
     */
    private void upsertSubmissionRecord(EvvSubmissionContext ctx, UUID agencyId,
                                         EvvSubmissionStatus status, String aggregatorVisitId) {
        EvvSubmissionRecord record = submissionRecordRepository
                .findByEvvRecordId(ctx.evvRecordId())
                .orElseGet(EvvSubmissionRecord::new);
        record.setEvvRecordId(ctx.evvRecordId());
        record.setAgencyId(agencyId);
        record.setAggregatorType(ctx.aggregatorType().name());
        record.setSubmissionMode("REAL_TIME");
        record.setStatus(status.name());
        record.setAggregatorVisitId(aggregatorVisitId);
        record.setSubmittedAt(LocalDateTime.now(ZoneOffset.UTC));
        submissionRecordRepository.save(record);
    }

    private UUID resolvePayerId(Shift shift) {
        if (shift.getAuthorizationId() == null) return null;
        return authorizationRepository.findById(shift.getAuthorizationId())
                .map(Authorization::getPayerId)
                .orElse(null);
    }

    private String resolveStateCode(Shift shift, Payer payer) {
        // Client.serviceState overrides payer.state for border-county agencies serving clients
        // in two different states.
        Client client = clientRepository.findById(shift.getClientId()).orElse(null);
        if (client != null && client.getServiceState() != null) {
            return client.getServiceState();
        }
        if (payer != null && payer.getState() != null) {
            return payer.getState();
        }
        return null;
    }
}
