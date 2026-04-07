package com.hcare.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CaregiverCredentialRepository extends JpaRepository<CaregiverCredential, UUID> {
    List<CaregiverCredential> findByCaregiverId(UUID caregiverId);

    Page<CaregiverCredential> findByCaregiverId(UUID caregiverId, Pageable pageable);

    /**
     * Returns credentials for the agency whose expiryDate falls within [from, to] (inclusive).
     * Used by DashboardService to find credentials expiring within the next 30 days.
     * NOTE: The agencyFilter @Filter is active on this entity so agencyId is already scoped
     * by the Hibernate filter; the agencyId parameter here is redundant but adds an explicit
     * safety predicate for queries run outside a filtered session (e.g. batch jobs).
     */
    List<CaregiverCredential> findByAgencyIdAndExpiryDateBetween(UUID agencyId,
                                                                  LocalDate from,
                                                                  LocalDate to);
}
