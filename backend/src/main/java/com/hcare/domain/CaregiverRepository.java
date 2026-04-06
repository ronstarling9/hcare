package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CaregiverRepository extends JpaRepository<Caregiver, UUID> {
    List<Caregiver> findByAgencyId(UUID agencyId);

    boolean existsByIdAndAgencyId(UUID id, UUID agencyId);
}
