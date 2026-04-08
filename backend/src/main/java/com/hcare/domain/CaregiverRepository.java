package com.hcare.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaregiverRepository extends JpaRepository<Caregiver, UUID> {
    List<Caregiver> findByAgencyId(UUID agencyId);
    Page<Caregiver> findByAgencyId(UUID agencyId, Pageable pageable);

    boolean existsByIdAndAgencyId(UUID id, UUID agencyId);
    Optional<Caregiver> findByIdAndAgencyId(UUID id, UUID agencyId);
}
