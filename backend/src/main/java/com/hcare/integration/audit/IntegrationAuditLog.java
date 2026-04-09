package com.hcare.integration.audit;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Append-only audit record for integration operations (EVV submissions, billing, payroll).
 *
 * <p>No Hibernate agencyFilter is applied — this table is intentionally cross-tenant.
 * All write paths use {@link IntegrationAuditWriter} which applies {@code REQUIRES_NEW}
 * transaction isolation. Query by {@code agencyId} to restrict to a single tenant.
 */
@Entity
@Table(name = "integration_audit_log")
@Immutable
public class IntegrationAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(length = 100, nullable = false)
    private String connector;

    @Column(length = 20, nullable = false)
    private String operation;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    protected IntegrationAuditLog() {}

    private IntegrationAuditLog(UUID agencyId, UUID entityId, String connector, String operation,
                                boolean success, long durationMs, String errorCode) {
        this.agencyId = agencyId;
        this.entityId = entityId;
        this.connector = connector;
        this.operation = operation;
        this.success = success;
        this.durationMs = durationMs;
        this.errorCode = errorCode;
    }

    @PrePersist
    void prePersist() {
        this.recordedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public static IntegrationAuditLog of(UUID agencyId, UUID entityId, String connector,
                                         String operation, boolean success, long durationMs,
                                         String errorCode) {
        return new IntegrationAuditLog(agencyId, entityId, connector, operation,
                                       success, durationMs, errorCode);
    }

    public UUID getId() { return id; }
    public UUID getAgencyId() { return agencyId; }
    public UUID getEntityId() { return entityId; }
    public String getConnector() { return connector; }
    public String getOperation() { return operation; }
    public boolean isSuccess() { return success; }
    public long getDurationMs() { return durationMs; }
    public String getErrorCode() { return errorCode; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
}
