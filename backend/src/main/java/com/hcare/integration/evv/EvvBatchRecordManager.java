package com.hcare.integration.evv;

import com.hcare.domain.EvvRecord;
import com.hcare.domain.EvvRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Transaction-demarcated helper for {@link EvvBatchDrainJob}.
 *
 * <p>Extracted as a separate Spring-managed bean so that {@code @Transactional(REQUIRES_NEW)}
 * methods are invoked through the AOP proxy — self-invocation from {@code EvvBatchDrainJob}
 * would bypass the proxy and render the transaction annotations ineffective.
 *
 * <p>C11: {@link #finalizeRecord} atomically writes both the {@link EvvSubmissionRecord} outcome
 * and the {@link EvvRecord#setAggregatorVisitId} update in a single {@code REQUIRES_NEW}
 * transaction, eliminating the previous two-step self-call race window.
 */
@Component
public class EvvBatchRecordManager {

    private static final Logger log = LoggerFactory.getLogger(EvvBatchRecordManager.class);

    private final EvvSubmissionRecordSystemRepository systemRepo;
    private final EvvRecordRepository evvRecordRepository;

    public EvvBatchRecordManager(
            EvvSubmissionRecordSystemRepository systemRepo,
            EvvRecordRepository evvRecordRepository) {
        this.systemRepo = systemRepo;
        this.evvRecordRepository = evvRecordRepository;
    }

    /**
     * Tx 1: atomically claim a record by transitioning PENDING → IN_FLIGHT.
     *
     * @param recordId the submission record id
     * @return 1 if claimed, 0 if already claimed by another node
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int claimRecord(UUID recordId) {
        return systemRepo.markInFlight(recordId);
    }

    /**
     * Tx 2: finalize a record AND update {@link EvvRecord#setAggregatorVisitId} in the SAME
     * transaction (C11 atomicity).
     *
     * @param recordId          the submission record id
     * @param status            terminal status string (e.g. "SUBMITTED", "REJECTED")
     * @param aggregatorVisitId visitor id returned by the aggregator, may be null
     * @param evvRecordId       the linked EVV record id for the C11 dual-write, may be null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeRecord(UUID recordId, String status, String aggregatorVisitId,
            UUID evvRecordId) {
        systemRepo.markFinal(recordId, status, aggregatorVisitId);
        if (aggregatorVisitId != null && evvRecordId != null) {
            EvvRecord evvRecord = evvRecordRepository.findById(evvRecordId).orElse(null);
            if (evvRecord != null) {
                evvRecord.setAggregatorVisitId(aggregatorVisitId);
                evvRecordRepository.save(evvRecord);
            } else {
                log.warn(
                        "EvvBatchRecordManager: EvvRecord not found for C11 dual-write: evvRecordId={}",
                        evvRecordId);
            }
        }
    }
}
