package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RecurrencePatternRepository extends JpaRepository<RecurrencePattern, UUID> {

    /** Returns all recurrence patterns belonging to the given agency. */
    @Transactional(readOnly = true)
    List<RecurrencePattern> findByAgencyId(UUID agencyId);

    @Transactional(readOnly = true)
    @Query("SELECT rp FROM RecurrencePattern rp WHERE rp.active = true " +
           "AND rp.generatedThrough < :horizon " +
           "AND (rp.endDate IS NULL OR rp.endDate >= :today)")
    List<RecurrencePattern> findActivePatternsBehindHorizon(@Param("horizon") LocalDate horizon,
                                                             @Param("today") LocalDate today);
}
