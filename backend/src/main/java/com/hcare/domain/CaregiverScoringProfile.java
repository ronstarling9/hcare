package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

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

    @Column(name = "completed_shifts_last_90_days", nullable = false)
    private int completedShiftsLast90Days = 0;

    @Column(name = "cancelled_shifts_last_90_days", nullable = false)
    private int cancelledShiftsLast90Days = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now(ZoneOffset.UTC);

    protected CaregiverScoringProfile() {}

    public CaregiverScoringProfile(UUID caregiverId, UUID agencyId) {
        this.caregiverId = caregiverId;
        this.agencyId = agencyId;
    }

    /** Called by onShiftCompleted listener — adds shift hours and increments completion count. */
    public void updateAfterShiftCompletion(BigDecimal hoursWorked) {
        this.currentWeekHours = this.currentWeekHours.add(hoursWorked);
        this.completedShiftsLast90Days++;
        recalculateCancelRate();
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    /** Called by onShiftCancelled listener — increments cancel count and recalculates rate. */
    public void updateAfterShiftCancellation() {
        this.cancelledShiftsLast90Days++;
        recalculateCancelRate();
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    /** Called by the weekly @Scheduled job each Monday to reset OT-risk scoring data. */
    public void resetWeeklyHours() {
        this.currentWeekHours = BigDecimal.ZERO;
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    private void recalculateCancelRate() {
        int total = completedShiftsLast90Days + cancelledShiftsLast90Days;
        this.cancelRateLast90Days = total == 0 ? BigDecimal.ZERO
            : BigDecimal.valueOf(cancelledShiftsLast90Days)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public UUID getCaregiverId() { return caregiverId; }
    public UUID getAgencyId() { return agencyId; }
    public BigDecimal getCancelRateLast90Days() { return cancelRateLast90Days; }
    public BigDecimal getCurrentWeekHours() { return currentWeekHours; }
    public int getCompletedShiftsLast90Days() { return completedShiftsLast90Days; }
    public int getCancelledShiftsLast90Days() { return cancelledShiftsLast90Days; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
