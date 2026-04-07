package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

public interface CaregiverAvailabilityRepository extends JpaRepository<CaregiverAvailability, UUID> {
    List<CaregiverAvailability> findByCaregiverId(UUID caregiverId);

    @Transactional
    @Modifying
    @Query("DELETE FROM CaregiverAvailability a WHERE a.caregiverId = :caregiverId AND a.agencyId = :agencyId")
    void deleteByCaregiverIdAndAgencyId(@Param("caregiverId") UUID caregiverId, @Param("agencyId") UUID agencyId);
}
