package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AdlTaskCompletionRepository extends JpaRepository<AdlTaskCompletion, UUID> {
    List<AdlTaskCompletion> findByShiftId(UUID shiftId);
}
