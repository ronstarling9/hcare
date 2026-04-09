package com.hcare.integration.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IntegrationAuditLogRepository extends JpaRepository<IntegrationAuditLog, UUID> {

    List<IntegrationAuditLog> findByAgencyIdAndConnectorOrderByRecordedAtDesc(
            UUID agencyId, String connector);

    List<IntegrationAuditLog> findByEntityId(UUID entityId);
}
