package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CarePlanRepository extends JpaRepository<CarePlan, UUID> {
    List<CarePlan> findByClientId(UUID clientId);
    Optional<CarePlan> findByClientIdAndStatus(UUID clientId, CarePlanStatus status);
}
