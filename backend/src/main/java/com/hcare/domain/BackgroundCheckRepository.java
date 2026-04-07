package com.hcare.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface BackgroundCheckRepository extends JpaRepository<BackgroundCheck, UUID> {
    List<BackgroundCheck> findByCaregiverId(UUID caregiverId);

    Page<BackgroundCheck> findByCaregiverId(UUID caregiverId, Pageable pageable);
}
