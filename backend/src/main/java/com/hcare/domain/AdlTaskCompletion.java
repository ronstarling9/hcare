package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "adl_task_completions",
    uniqueConstraints = @UniqueConstraint(name = "uq_adl_task_completion", columnNames = {"shift_id", "adl_task_id"}))
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class AdlTaskCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "shift_id", nullable = false)
    private UUID shiftId;

    @Column(name = "adl_task_id", nullable = false)
    private UUID adlTaskId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    @Column(name = "caregiver_notes", columnDefinition = "TEXT")
    private String caregiverNotes;

    protected AdlTaskCompletion() {}

    public AdlTaskCompletion(UUID shiftId, UUID adlTaskId, UUID agencyId) {
        this.shiftId = shiftId;
        this.adlTaskId = adlTaskId;
        this.agencyId = agencyId;
        this.completedAt = LocalDateTime.now();
    }

    public void setCaregiverNotes(String caregiverNotes) { this.caregiverNotes = caregiverNotes; }

    public UUID getId() { return id; }
    public UUID getShiftId() { return shiftId; }
    public UUID getAdlTaskId() { return adlTaskId; }
    public UUID getAgencyId() { return agencyId; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public String getCaregiverNotes() { return caregiverNotes; }
}
