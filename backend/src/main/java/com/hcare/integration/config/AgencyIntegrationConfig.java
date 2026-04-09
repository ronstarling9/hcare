package com.hcare.integration.config;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Persists per-agency connector configuration for external EVV aggregators and billing systems.
 *
 * <p>Uniqueness for (agencyId, integrationType, stateCode, payerType) is enforced at the service
 * layer (check-before-insert inside a transaction). No DB unique constraint because COALESCE/NULL
 * handling for partial uniqueness is not H2-compatible.
 */
@Entity
@Table(name = "agency_integration_configs")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class AgencyIntegrationConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "integration_type", length = 30, nullable = false)
    private String integrationType;

    @Column(name = "connector_class", length = 100, nullable = false)
    private String connectorClass;

    @Column(name = "state_code", columnDefinition = "CHAR(2)")
    private String stateCode;

    @Column(name = "payer_type", length = 20)
    private String payerType;

    @Column(name = "endpoint_url", length = 500)
    private String endpointUrl;

    @Column(name = "credentials_encrypted", nullable = false)
    private String credentialsEncrypted;

    @Column(name = "config_json")
    private String configJson;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected AgencyIntegrationConfig() {}

    public AgencyIntegrationConfig(UUID agencyId, String integrationType, String connectorClass,
                                   String stateCode, String payerType, String endpointUrl,
                                   String credentialsEncrypted, String configJson, boolean active) {
        this.agencyId = agencyId;
        this.integrationType = integrationType;
        this.connectorClass = connectorClass;
        this.stateCode = stateCode;
        this.payerType = payerType;
        this.endpointUrl = endpointUrl;
        this.credentialsEncrypted = credentialsEncrypted;
        this.configJson = configJson;
        this.active = active;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    // Setters
    public void setIntegrationType(String integrationType) { this.integrationType = integrationType; }
    public void setConnectorClass(String connectorClass) { this.connectorClass = connectorClass; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }
    public void setPayerType(String payerType) { this.payerType = payerType; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }
    public void setCredentialsEncrypted(String credentialsEncrypted) { this.credentialsEncrypted = credentialsEncrypted; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public void setActive(boolean active) { this.active = active; }

    // Getters
    public UUID getId() { return id; }
    public UUID getAgencyId() { return agencyId; }
    public String getIntegrationType() { return integrationType; }
    public String getConnectorClass() { return connectorClass; }
    public String getStateCode() { return stateCode; }
    public String getPayerType() { return payerType; }
    public String getEndpointUrl() { return endpointUrl; }
    public String getCredentialsEncrypted() { return credentialsEncrypted; }
    public String getConfigJson() { return configJson; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
