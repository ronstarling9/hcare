package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "adl_tasks")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class AdlTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "care_plan_id", nullable = false)
    private UUID carePlanId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "assistance_level", nullable = false, length = 30)
    private AssistanceLevel assistanceLevel;

    @Column(length = 100)
    private String frequency;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected AdlTask() {}

    public AdlTask(UUID carePlanId, UUID agencyId, String name, AssistanceLevel assistanceLevel) {
        this.carePlanId = carePlanId;
        this.agencyId = agencyId;
        this.name = name;
        this.assistanceLevel = assistanceLevel;
    }

    public UUID getId() { return id; }
    public UUID getCarePlanId() { return carePlanId; }
    public UUID getAgencyId() { return agencyId; }
    public String getName() { return name; }
    public String getInstructions() { return instructions; }
    public AssistanceLevel getAssistanceLevel() { return assistanceLevel; }
    public String getFrequency() { return frequency; }
    public int getSortOrder() { return sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
