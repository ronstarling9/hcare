package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface BackgroundCheckRepository extends JpaRepository<BackgroundCheck, UUID> {
    List<BackgroundCheck> findByCaregiverId(UUID caregiverId);
}
