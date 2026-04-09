package com.hcare.integration.evv;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.util.UUID;

@Entity
@Table(name = "agency_evv_credentials")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class AgencyEvvCredentials {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "aggregator_type", nullable = false, length = 30)
    private String aggregatorType;

    @Column(name = "credentials_encrypted", nullable = false, columnDefinition = "TEXT")
    private String credentialsEncrypted;

    @Column(name = "endpoint_override", length = 500)
    private String endpointOverride;

    @Column(nullable = false)
    private boolean active = true;

    protected AgencyEvvCredentials() {}

    public AgencyEvvCredentials(UUID agencyId, String aggregatorType, String credentialsEncrypted,
                                String endpointOverride, boolean active) {
        this.agencyId = agencyId;
        this.aggregatorType = aggregatorType;
        this.credentialsEncrypted = credentialsEncrypted;
        this.endpointOverride = endpointOverride;
        this.active = active;
    }

    public UUID getId() { return id; }
    public UUID getAgencyId() { return agencyId; }
    public String getAggregatorType() { return aggregatorType; }
    public String getCredentialsEncrypted() { return credentialsEncrypted; }
    public String getEndpointOverride() { return endpointOverride; }
    public boolean isActive() { return active; }

    public void setCredentialsEncrypted(String credentialsEncrypted) {
        this.credentialsEncrypted = credentialsEncrypted;
    }

    public void setEndpointOverride(String endpointOverride) {
        this.endpointOverride = endpointOverride;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
