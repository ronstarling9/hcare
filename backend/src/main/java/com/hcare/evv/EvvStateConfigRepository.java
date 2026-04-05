package com.hcare.evv;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface EvvStateConfigRepository extends JpaRepository<EvvStateConfig, UUID> {
    Optional<EvvStateConfig> findByStateCode(String stateCode);
}
