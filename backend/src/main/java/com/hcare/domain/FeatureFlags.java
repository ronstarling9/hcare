package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "feature_flags")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class FeatureFlags {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false, unique = true)
    private UUID agencyId;

    @Column(name = "ai_scheduling_enabled", nullable = false)
    private boolean aiSchedulingEnabled = false;

    @Column(name = "family_portal_enabled", nullable = false)
    private boolean familyPortalEnabled = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);

    protected FeatureFlags() {}

    public FeatureFlags(UUID agencyId) {
        this.agencyId = agencyId;
    }

    public void setAiSchedulingEnabled(boolean enabled) { this.aiSchedulingEnabled = enabled; }
    public void setFamilyPortalEnabled(boolean enabled) { this.familyPortalEnabled = enabled; }

    public UUID getId() { return id; }
    public UUID getAgencyId() { return agencyId; }
    public boolean isAiSchedulingEnabled() { return aiSchedulingEnabled; }
    public boolean isFamilyPortalEnabled() { return familyPortalEnabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
