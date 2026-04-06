package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "recurrence_patterns")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class RecurrencePattern {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "caregiver_id")
    private UUID caregiverId;

    @Column(name = "service_type_id", nullable = false)
    private UUID serviceTypeId;

    @Column(name = "authorization_id")
    private UUID authorizationId;

    @Column(name = "scheduled_start_time", nullable = false)
    private LocalTime scheduledStartTime;

    @Column(name = "scheduled_duration_minutes", nullable = false)
    private int scheduledDurationMinutes;

    // JSON array of DayOfWeek names e.g. ["MONDAY","WEDNESDAY","FRIDAY"]
    @Column(name = "days_of_week", nullable = false, columnDefinition = "TEXT")
    private String daysOfWeek;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    // Last date for which Shift rows have been generated.
    // Initialized to startDate - 1 day so the first generateForPattern call starts from startDate.
    @Column(name = "generated_through", nullable = false)
    private LocalDate generatedThrough;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    // Incremented by Hibernate on every UPDATE. Concurrent saves (e.g. pattern edit + nightly
    // scheduler) throw ObjectOptimisticLockingFailureException — caller must retry.
    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);

    protected RecurrencePattern() {}

    public RecurrencePattern(UUID agencyId, UUID clientId, UUID serviceTypeId,
                              LocalTime scheduledStartTime, int scheduledDurationMinutes,
                              String daysOfWeek, LocalDate startDate) {
        this.agencyId = agencyId;
        this.clientId = clientId;
        this.serviceTypeId = serviceTypeId;
        this.scheduledStartTime = scheduledStartTime;
        this.scheduledDurationMinutes = scheduledDurationMinutes;
        this.daysOfWeek = daysOfWeek;
        this.startDate = startDate;
        this.generatedThrough = startDate.minusDays(1);
    }

    public void setCaregiverId(UUID caregiverId) { this.caregiverId = caregiverId; }
    public void setAuthorizationId(UUID authorizationId) { this.authorizationId = authorizationId; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public void setActive(boolean active) { this.active = active; }
    public void setGeneratedThrough(LocalDate generatedThrough) { this.generatedThrough = generatedThrough; }
    public void setScheduledStartTime(LocalTime scheduledStartTime) { this.scheduledStartTime = scheduledStartTime; }
    public void setScheduledDurationMinutes(int scheduledDurationMinutes) { this.scheduledDurationMinutes = scheduledDurationMinutes; }
    public void setDaysOfWeek(String daysOfWeek) { this.daysOfWeek = daysOfWeek; }

    public UUID getId() { return id; }
    public UUID getAgencyId() { return agencyId; }
    public UUID getClientId() { return clientId; }
    public UUID getCaregiverId() { return caregiverId; }
    public UUID getServiceTypeId() { return serviceTypeId; }
    public UUID getAuthorizationId() { return authorizationId; }
    public LocalTime getScheduledStartTime() { return scheduledStartTime; }
    public int getScheduledDurationMinutes() { return scheduledDurationMinutes; }
    public String getDaysOfWeek() { return daysOfWeek; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public LocalDate getGeneratedThrough() { return generatedThrough; }
    public boolean isActive() { return active; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
