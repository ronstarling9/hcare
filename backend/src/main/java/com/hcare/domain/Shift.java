package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shifts")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "source_pattern_id")
    private UUID sourcePatternId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "caregiver_id")
    private UUID caregiverId;

    @Column(name = "service_type_id", nullable = false)
    private UUID serviceTypeId;

    @Column(name = "authorization_id")
    private UUID authorizationId;

    @Column(name = "scheduled_start", nullable = false)
    private LocalDateTime scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private LocalDateTime scheduledEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShiftStatus status;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Shift() {}

    /**
     * Status is set automatically: OPEN when caregiverId is null, ASSIGNED otherwise.
     * sourcePatternId is null for ad-hoc shifts not generated from a RecurrencePattern.
     */
    public Shift(UUID agencyId, UUID sourcePatternId, UUID clientId, UUID caregiverId,
                 UUID serviceTypeId, UUID authorizationId,
                 LocalDateTime scheduledStart, LocalDateTime scheduledEnd) {
        this.agencyId = agencyId;
        this.sourcePatternId = sourcePatternId;
        this.clientId = clientId;
        this.caregiverId = caregiverId;
        this.serviceTypeId = serviceTypeId;
        this.authorizationId = authorizationId;
        this.scheduledStart = scheduledStart;
        this.scheduledEnd = scheduledEnd;
        this.status = caregiverId != null ? ShiftStatus.ASSIGNED : ShiftStatus.OPEN;
    }

    public void setStatus(ShiftStatus status) { this.status = status; }
    public void setCaregiverId(UUID caregiverId) { this.caregiverId = caregiverId; }
    public void setNotes(String notes) { this.notes = notes; }

    public UUID getId() { return id; }
    public UUID getAgencyId() { return agencyId; }
    public UUID getSourcePatternId() { return sourcePatternId; }
    public UUID getClientId() { return clientId; }
    public UUID getCaregiverId() { return caregiverId; }
    public UUID getServiceTypeId() { return serviceTypeId; }
    public UUID getAuthorizationId() { return authorizationId; }
    public LocalDateTime getScheduledStart() { return scheduledStart; }
    public LocalDateTime getScheduledEnd() { return scheduledEnd; }
    public ShiftStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
