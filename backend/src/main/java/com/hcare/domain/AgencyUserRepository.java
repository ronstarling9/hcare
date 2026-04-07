package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgencyUserRepository extends JpaRepository<AgencyUser, UUID> {
    Optional<AgencyUser> findByEmail(String email);
    List<AgencyUser> findByAgencyId(UUID agencyId);
    long countByAgencyIdAndRole(UUID agencyId, UserRole role);
}
