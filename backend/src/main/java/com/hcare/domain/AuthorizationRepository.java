package com.hcare.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AuthorizationRepository extends JpaRepository<Authorization, UUID> {
    List<Authorization> findByClientId(UUID clientId);
    List<Authorization> findByAgencyId(UUID agencyId);

    Page<Authorization> findByClientId(UUID clientId, Pageable pageable);
    Page<Authorization> findByPayerId(UUID payerId, Pageable pageable);
}
