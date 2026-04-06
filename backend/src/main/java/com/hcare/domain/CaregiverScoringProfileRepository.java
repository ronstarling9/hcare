package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface CaregiverScoringProfileRepository extends JpaRepository<CaregiverScoringProfile, UUID> {

    Optional<CaregiverScoringProfile> findByCaregiverId(UUID caregiverId);

    /**
     * Bulk-resets currentWeekHours to 0 for ALL profiles across ALL agencies.
     * Intentionally bypasses the Hibernate agencyFilter — this is a global weekly
     * maintenance operation that must touch every agency's data regardless of tenant.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CaregiverScoringProfile p SET p.currentWeekHours = 0, p.updatedAt = :now")
    void resetAllWeeklyHours(@Param("now") LocalDateTime now);
}
