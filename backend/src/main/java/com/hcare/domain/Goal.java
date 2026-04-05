package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "goals")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "care_plan_id", nullable = false)
    private UUID carePlanId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoalStatus status = GoalStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);

    protected Goal() {}

    public Goal(UUID carePlanId, UUID agencyId, String description) {
        this.carePlanId = carePlanId;
        this.agencyId = agencyId;
        this.description = description;
    }

    public UUID getId() { return id; }
    public UUID getCarePlanId() { return carePlanId; }
    public UUID getAgencyId() { return agencyId; }
    public String getDescription() { return description; }
    public LocalDate getTargetDate() { return targetDate; }
    public GoalStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
