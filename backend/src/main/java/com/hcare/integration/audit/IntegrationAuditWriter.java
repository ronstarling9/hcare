package com.hcare.integration.audit;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class IntegrationAuditWriter {

    private final IntegrationAuditLogRepository repo;

    public IntegrationAuditWriter(IntegrationAuditLogRepository repo) {
        this.repo = repo;
    }

    public void record(UUID agencyId, UUID entityId, String connector, String operation,
                       boolean success, long durationMs, String errorCode) {
        repo.save(IntegrationAuditLog.of(agencyId, entityId, connector, operation,
                                         success, durationMs, errorCode));
    }
}
