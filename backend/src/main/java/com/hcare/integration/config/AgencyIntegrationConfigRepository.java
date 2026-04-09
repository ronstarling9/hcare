package com.hcare.integration.config;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgencyIntegrationConfigRepository extends JpaRepository<AgencyIntegrationConfig, UUID> {

    List<AgencyIntegrationConfig> findByAgencyIdAndActiveTrue(UUID agencyId);

    Optional<AgencyIntegrationConfig> findByAgencyIdAndIntegrationTypeAndActiveTrue(
            UUID agencyId, String integrationType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("FROM AgencyIntegrationConfig c WHERE c.agencyId = :agencyId AND c.integrationType = :type " +
           "AND (:stateCode IS NULL AND c.stateCode IS NULL OR c.stateCode = :stateCode) " +
           "AND (:payerType IS NULL AND c.payerType IS NULL OR c.payerType = :payerType)")
    Optional<AgencyIntegrationConfig> findForUpdate(
            @Param("agencyId") UUID agencyId,
            @Param("type") String type,
            @Param("stateCode") String stateCode,
            @Param("payerType") String payerType);
}
