package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AuthorizationRepository extends JpaRepository<Authorization, UUID> {
    List<Authorization> findByClientId(UUID clientId);
    List<Authorization> findByAgencyId(UUID agencyId);
}
