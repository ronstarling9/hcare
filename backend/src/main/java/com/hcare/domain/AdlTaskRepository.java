package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AdlTaskRepository extends JpaRepository<AdlTask, UUID> {
    List<AdlTask> findByCarePlanIdOrderBySortOrder(UUID carePlanId);
}
