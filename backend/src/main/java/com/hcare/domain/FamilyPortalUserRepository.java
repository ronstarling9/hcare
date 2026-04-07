package com.hcare.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FamilyPortalUserRepository extends JpaRepository<FamilyPortalUser, UUID> {
    Optional<FamilyPortalUser> findByAgencyIdAndEmail(UUID agencyId, String email);
    List<FamilyPortalUser> findByClientId(UUID clientId);

    Page<FamilyPortalUser> findByClientId(UUID clientId, Pageable pageable);
}
