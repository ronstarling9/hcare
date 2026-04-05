package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "incident_reports")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class IncidentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    // Nullable: not all incidents are shift-related (e.g. general complaints)
    @Column(name = "shift_id")
    private UUID shiftId;

    // Polymorphic: AGENCY_USER or CAREGIVER — no FK constraint
    @Column(name = "reported_by_type", nullable = false, length = 30)
    private String reportedByType;

    @Column(name = "reported_by_id", nullable = false)
    private UUID reportedById;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentSeverity severity;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected IncidentReport() {}

    public IncidentReport(UUID agencyId, String reportedByType, UUID reportedById,
                          String description, IncidentSeverity severity, LocalDateTime occurredAt) {
        this.agencyId = agencyId;
        this.reportedByType = reportedByType;
        this.reportedById = reportedById;
        this.description = description;
        this.severity = severity;
        this.occurredAt = occurredAt;
    }

    public void setShiftId(UUID shiftId) { this.shiftId = shiftId; }

    public UUID getId() { return id; }
    public UUID getAgencyId() { return agencyId; }
    public UUID getShiftId() { return shiftId; }
    public String getReportedByType() { return reportedByType; }
    public UUID getReportedById() { return reportedById; }
    public String getDescription() { return description; }
    public IncidentSeverity getSeverity() { return severity; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
