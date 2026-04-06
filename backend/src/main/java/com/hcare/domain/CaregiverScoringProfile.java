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

    @Column(name = "cancel_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal cancelRate = BigDecimal.ZERO;

    @Column(name = "current_week_hours", nullable = false, precision = 5, scale = 2)
    private BigDecimal currentWeekHours = BigDecimal.ZERO;

    @Column(name = "total_completed_shifts", nullable = false)
    private int totalCompletedShifts = 0;

    @Column(name = "total_cancelled_shifts", nullable = false)
    private int totalCancelledShifts = 0;

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
        this.totalCompletedShifts++;
        recalculateCancelRate();
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    /** Called by onShiftCancelled listener — increments cancel count and recalculates rate. */
    public void updateAfterShiftCancellation() {
        this.totalCancelledShifts++;
        recalculateCancelRate();
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    /** Called by the weekly @Scheduled job each Monday to reset OT-risk scoring data. */
    public void resetWeeklyHours() {
        this.currentWeekHours = BigDecimal.ZERO;
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    private void recalculateCancelRate() {
        int total = totalCompletedShifts + totalCancelledShifts;
        this.cancelRate = total == 0 ? BigDecimal.ZERO
            : BigDecimal.valueOf(totalCancelledShifts)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public UUID getCaregiverId() { return caregiverId; }
    public UUID getAgencyId() { return agencyId; }
    public BigDecimal getCancelRate() { return cancelRate; }
    public BigDecimal getCurrentWeekHours() { return currentWeekHours; }
    public int getTotalCompletedShifts() { return totalCompletedShifts; }
    public int getTotalCancelledShifts() { return totalCancelledShifts; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
