package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RecurrencePatternRepository extends JpaRepository<RecurrencePattern, UUID> {

    /**
     * Returns active patterns whose generatedThrough frontier is behind the given horizon
     * and whose endDate has not yet passed.
     * Called by the nightly scheduler — no TenantContext set, so the Hibernate @Filter is
     * not active. This is intentional: the nightly job processes all agencies.
     */
    @Transactional(readOnly = true)
    @Query("SELECT rp FROM RecurrencePattern rp WHERE rp.active = true " +
           "AND rp.generatedThrough < :horizon " +
           "AND (rp.endDate IS NULL OR rp.endDate >= :today)")
    List<RecurrencePattern> findActivePatternsBehindHorizon(@Param("horizon") LocalDate horizon,
                                                             @Param("today") LocalDate today);
}
