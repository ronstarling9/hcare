package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    List<Shift> findByClientIdAndScheduledStartBetween(UUID clientId,
                                                        LocalDateTime start,
                                                        LocalDateTime end);

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
}
