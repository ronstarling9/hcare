package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface FeatureFlagsRepository extends JpaRepository<FeatureFlags, UUID> {
    Optional<FeatureFlags> findByAgencyId(UUID agencyId);
}
