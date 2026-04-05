package com.hcare.audit;

import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class PhiAuditService {

    private final PhiAuditRepository repository;

    public PhiAuditService(PhiAuditRepository repository) {
        this.repository = repository;
    }

    public void logRead(UUID userId, UUID agencyId, ResourceType resourceType,
                        UUID resourceId, String ipAddress) {
        repository.save(PhiAuditLog.forUser(userId, agencyId, resourceType,
            resourceId, AuditAction.READ, ipAddress));
    }

    public void logWrite(UUID userId, UUID agencyId, ResourceType resourceType,
                         UUID resourceId, String ipAddress) {
        repository.save(PhiAuditLog.forUser(userId, agencyId, resourceType,
            resourceId, AuditAction.WRITE, ipAddress));
    }

    public void logDelete(UUID userId, UUID agencyId, ResourceType resourceType,
                          UUID resourceId, String ipAddress) {
        repository.save(PhiAuditLog.forUser(userId, agencyId, resourceType,
            resourceId, AuditAction.DELETE, ipAddress));
    }

    public void logExport(UUID userId, UUID agencyId, ResourceType resourceType,
                          UUID resourceId, String ipAddress) {
        repository.save(PhiAuditLog.forUser(userId, agencyId, resourceType,
            resourceId, AuditAction.EXPORT, ipAddress));
    }
}
