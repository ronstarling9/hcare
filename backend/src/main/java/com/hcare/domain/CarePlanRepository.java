package com.hcare.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CarePlanRepository extends JpaRepository<CarePlan, UUID> {
    List<CarePlan> findByClientIdOrderByPlanVersionAsc(UUID clientId);

    Page<CarePlan> findByClientIdOrderByPlanVersionAsc(UUID clientId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CarePlan> findByClientIdAndStatus(UUID clientId, CarePlanStatus status);

    @Query("SELECT COALESCE(MAX(p.planVersion), 0) FROM CarePlan p WHERE p.clientId = :clientId")
    int findMaxPlanVersionByClientId(UUID clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM CarePlan p WHERE p.id = :id")
    Optional<CarePlan> findByIdWithLock(UUID id);
}
