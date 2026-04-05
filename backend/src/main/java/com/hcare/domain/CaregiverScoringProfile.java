package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

// Pre-computed scoring signals. Updated asynchronously via Spring events when shifts
// complete or cancel (Plan 5). Never computed on the match-request path.
@Entity
@Table(name = "caregiver_scoring_profiles")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class CaregiverScoringProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "caregiver_id", nullable = false, unique = true)
    private UUID caregiverId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "cancel_rate_last_90_days", nullable = false, precision = 5, scale = 4)
    private BigDecimal cancelRateLast90Days = BigDecimal.ZERO;

    @Column(name = "current_week_hours", nullable = false, precision = 5, scale = 2)
    private BigDecimal currentWeekHours = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now(ZoneOffset.UTC);

    protected CaregiverScoringProfile() {}

    public CaregiverScoringProfile(UUID caregiverId, UUID agencyId) {
        this.caregiverId = caregiverId;
        this.agencyId = agencyId;
    }

    public void updateAfterShiftCompletion(BigDecimal hoursWorked) {
        this.currentWeekHours = this.currentWeekHours.add(hoursWorked);
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() { return id; }
    public UUID getCaregiverId() { return caregiverId; }
    public UUID getAgencyId() { return agencyId; }
    public BigDecimal getCancelRateLast90Days() { return cancelRateLast90Days; }
    public BigDecimal getCurrentWeekHours() { return currentWeekHours; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
