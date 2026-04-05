package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CaregiverAvailabilityRepository extends JpaRepository<CaregiverAvailability, UUID> {
    List<CaregiverAvailability> findByCaregiverId(UUID caregiverId);
}
