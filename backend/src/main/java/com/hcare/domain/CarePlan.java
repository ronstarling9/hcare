package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "care_plans")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class CarePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    // Business version counter. One ACTIVE plan per client enforced at service layer (Plan 6).
    @Column(name = "plan_version", nullable = false)
    private int planVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CarePlanStatus status = CarePlanStatus.DRAFT;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    // HHCS (skilled nursing) plans require clinical review sign-off.
    // PCS (non-medical personal care) plans leave these null.
    @Column(name = "reviewed_by_clinician_id")
    private UUID reviewedByClinicianId;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    protected CarePlan() {}

    public CarePlan(UUID clientId, UUID agencyId, int planVersion) {
        this.clientId = clientId;
        this.agencyId = agencyId;
        this.planVersion = planVersion;
    }

    public void activate() {
        this.status = CarePlanStatus.ACTIVE;
        this.activatedAt = LocalDateTime.now();
    }

    public void supersede() {
        this.status = CarePlanStatus.SUPERSEDED;
    }

    public UUID getId() { return id; }
    public UUID getClientId() { return clientId; }
    public UUID getAgencyId() { return agencyId; }
    public int getPlanVersion() { return planVersion; }
    public CarePlanStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getActivatedAt() { return activatedAt; }
    public UUID getReviewedByClinicianId() { return reviewedByClinicianId; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
}
