package com.hcare.integration.evv;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgencyEvvCredentialsRepository extends JpaRepository<AgencyEvvCredentials, UUID> {

    Optional<AgencyEvvCredentials> findByAgencyIdAndAggregatorTypeAndActiveTrue(
            UUID agencyId, String aggregatorType);
}
