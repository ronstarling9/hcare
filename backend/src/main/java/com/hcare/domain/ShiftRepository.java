package com.hcare.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    Page<Shift> findByAgencyIdAndScheduledStartBetween(UUID agencyId,
                                                        LocalDateTime start,
                                                        LocalDateTime end,
                                                        Pageable pageable);

    Page<Shift> findByAgencyIdAndStatusAndScheduledStartBetween(UUID agencyId,
                                                                  ShiftStatus status,
                                                                  LocalDateTime start,
                                                                  LocalDateTime end,
                                                                  Pageable pageable);

    List<Shift> findByClientIdAndScheduledStartBetween(UUID clientId,
                                                        LocalDateTime start,
                                                        LocalDateTime end);

    // Last visit — most recent COMPLETED shift for a client.
    Optional<Shift> findFirstByClientIdAndStatusOrderByScheduledStartDesc(
        UUID clientId, ShiftStatus status);

    // Upcoming visits — DB-side TOP 3 to avoid loading 90 days of shifts into memory.
    // Excludes CANCELLED and MISSED shifts via the excludedStatuses collection.
    List<Shift> findTop3ByClientIdAndStatusNotInAndScheduledStartAfterOrderByScheduledStartAsc(
        UUID clientId, Collection<ShiftStatus> excludedStatuses, LocalDateTime after);

    // Today's visit window — explicit agencyId predicate guards against edge cases where
    // the Hibernate agencyFilter may not activate for FAMILY_PORTAL-role requests.
    List<Shift> findByClientIdAndAgencyIdAndScheduledStartBetween(
        UUID clientId, UUID agencyId, LocalDateTime start, LocalDateTime end);

    List<Shift> findByCaregiverIdAndScheduledStartBetween(UUID caregiverId,
                                                           LocalDateTime start,
                                                           LocalDateTime end);

    /**
     * Bulk-deletes future unstarted shifts for a pattern (OPEN or ASSIGNED, scheduledStart > cutoff).
     * Explicitly includes agencyId in the WHERE clause — Hibernate @Filter does not apply to
     * bulk JPQL DELETE statements.
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Shift s WHERE s.sourcePatternId = :patternId " +
           "AND s.agencyId = :agencyId " +
           "AND s.scheduledStart > :cutoff " +
           "AND s.status IN :statuses")
    void deleteUnstartedFutureShifts(@Param("patternId") UUID patternId,
                                      @Param("agencyId") UUID agencyId,
                                      @Param("cutoff") LocalDateTime cutoff,
                                      @Param("statuses") Collection<ShiftStatus> statuses);

    /**
     * Returns all shifts for a caregiver whose scheduled window overlaps [start, end).
     * Uses a proper interval overlap predicate instead of a time-window heuristic.
     */
    @Query("""
        SELECT s FROM Shift s
        WHERE s.caregiverId = :caregiverId
          AND s.scheduledStart < :end
          AND s.scheduledEnd > :start
        """)
    List<Shift> findOverlapping(@Param("caregiverId") UUID caregiverId,
                                @Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

    List<Shift> findByCaregiverId(UUID caregiverId);

    Page<Shift> findByCaregiverId(UUID caregiverId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Shift s WHERE s.id = :id")
    Optional<Shift> findByIdForUpdate(@Param("id") UUID id);
}
