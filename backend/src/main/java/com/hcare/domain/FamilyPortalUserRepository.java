package com.hcare.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FamilyPortalUserRepository extends JpaRepository<FamilyPortalUser, UUID> {
    // findByAgencyIdAndEmail deliberately omitted: V12 migration allows the same email
    // at the same agency across multiple clients. Use findByClientIdAndAgencyIdAndEmail instead.
    Optional<FamilyPortalUser> findByClientIdAndAgencyIdAndEmail(UUID clientId, UUID agencyId, String email);
    List<FamilyPortalUser> findByClientId(UUID clientId);
    Page<FamilyPortalUser> findByClientId(UUID clientId, Pageable pageable);
}
