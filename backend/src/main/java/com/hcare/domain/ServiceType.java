package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "service_types")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class ServiceType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(name = "requires_evv", nullable = false)
    private boolean requiresEvv;

    // JSON array of CredentialType enum names — parsed at application layer, never queried at DB level
    @Column(name = "required_credentials", nullable = false, columnDefinition = "TEXT")
    private String requiredCredentials;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);

    protected ServiceType() {}

    public ServiceType(UUID agencyId, String name, String code,
                       boolean requiresEvv, String requiredCredentials) {
        this.agencyId = agencyId;
        this.name = name;
        this.code = code;
        this.requiresEvv = requiresEvv;
        this.requiredCredentials = requiredCredentials;
    }

    public UUID getId() { return id; }
    public UUID getAgencyId() { return agencyId; }
    public String getName() { return name; }
    public String getCode() { return code; }
    public boolean isRequiresEvv() { return requiresEvv; }
    public String getRequiredCredentials() { return requiredCredentials; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
