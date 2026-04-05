package com.hcare.audit;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

// @FilterDef is NOT repeated here — it is declared globally in domain/package-info.java
// and Hibernate registers it for the entire persistence unit regardless of Java package.
// Declaring it a second time with the same name causes a DuplicateMappingException at startup.
@Entity
@Table(name = "phi_audit_logs")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class PhiAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "family_portal_user_id")
    private UUID familyPortalUserId;

    @Column(name = "system_job_id")
    private String systemJobId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 50)
    private ResourceType resourceType;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditAction action;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt = LocalDateTime.now(ZoneOffset.UTC);

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    protected PhiAuditLog() {}

    private PhiAuditLog(UUID userId, UUID agencyId, ResourceType resourceType,
                        UUID resourceId, AuditAction action, String ipAddress) {
        this.userId = userId;
        this.agencyId = agencyId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.action = action;
        this.ipAddress = ipAddress;
    }

    static PhiAuditLog forUser(UUID userId, UUID agencyId, ResourceType type,
                               UUID resourceId, AuditAction action, String ip) {
        return new PhiAuditLog(userId, agencyId, type, resourceId, action, ip);
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getAgencyId() { return agencyId; }
    public ResourceType getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
    public AuditAction getAction() { return action; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public String getIpAddress() { return ipAddress; }
}
