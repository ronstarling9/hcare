package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface CaregiverScoringProfileRepository extends JpaRepository<CaregiverScoringProfile, UUID> {
    Optional<CaregiverScoringProfile> findByCaregiverId(UUID caregiverId);
}
