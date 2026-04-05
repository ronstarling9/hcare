package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(
    name = "caregiver_availability",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_caregiver_availability",
        columnNames = {"caregiver_id", "day_of_week", "start_time", "end_time"}
    )
)
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class CaregiverAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "caregiver_id", nullable = false)
    private UUID caregiverId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected CaregiverAvailability() {}

    // P1 scope: overnight slots (startTime > endTime) are not supported — the Plan 3
    // scheduler assumes same-day windows. Overnight support is deferred to P2.
    public CaregiverAvailability(UUID caregiverId, UUID agencyId,
                                  DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException(
                "startTime must be before endTime (overnight slots not supported in P1)");
        }
        this.caregiverId = caregiverId;
        this.agencyId = agencyId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public UUID getId() { return id; }
    public UUID getCaregiverId() { return caregiverId; }
    public UUID getAgencyId() { return agencyId; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
