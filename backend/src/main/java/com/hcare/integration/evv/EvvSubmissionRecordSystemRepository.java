package com.hcare.integration.evv;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * System-level repository for {@link EvvSubmissionRecord} — no Hibernate agencyFilter.
 * Used ONLY by {@link EvvBatchDrainJob} and the startup watchdog (C9/C14).
 * All methods use explicit agencyId parameters.
 */
@Repository
public interface EvvSubmissionRecordSystemRepository extends JpaRepository<EvvSubmissionRecord, UUID> {

    @Query("SELECT DISTINCT r.agencyId FROM EvvSubmissionRecord r WHERE r.submissionMode = 'BATCH' AND r.status = 'PENDING'")
    List<UUID> findDistinctAgenciesWithPendingBatch();

    @Query("SELECT r FROM EvvSubmissionRecord r WHERE r.agencyId = :agencyId AND r.submissionMode = 'BATCH' AND r.status = 'PENDING' ORDER BY r.createdAt ASC")
    List<EvvSubmissionRecord> findNextBatchPending(@Param("agencyId") UUID agencyId, Pageable pageable);

    /** Tx 1: claim a record by transitioning PENDING → IN_FLIGHT. Returns 1 if claimed, 0 if already claimed by another node. */
    @Modifying
    @Query("UPDATE EvvSubmissionRecord r SET r.status = 'IN_FLIGHT', r.inFlightSince = CURRENT_TIMESTAMP WHERE r.id = :id AND r.status = 'PENDING'")
    int markInFlight(@Param("id") UUID id);

    /** Tx 2: finalize a record after submission. */
    @Modifying
    @Query("UPDATE EvvSubmissionRecord r SET r.status = :status, r.aggregatorVisitId = :visitId, r.submittedAt = CURRENT_TIMESTAMP WHERE r.id = :id")
    void markFinal(@Param("id") UUID id, @Param("status") String status, @Param("visitId") String visitId);

    /** Startup watchdog: reset stale IN_FLIGHT rows back to PENDING. */
    @Modifying
    @Query("UPDATE EvvSubmissionRecord r SET r.status = 'PENDING', r.inFlightSince = NULL WHERE r.status = 'IN_FLIGHT' AND r.inFlightSince < :cutoff")
    int resetStaleInFlight(@Param("cutoff") java.time.LocalDateTime cutoff);
}
