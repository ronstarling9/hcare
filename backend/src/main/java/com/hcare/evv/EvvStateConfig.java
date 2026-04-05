package com.hcare.evv;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "evv_state_configs")
public class EvvStateConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "state_code", nullable = false, unique = true, columnDefinition = "CHAR(2)")
    private String stateCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_aggregator", nullable = false, length = 30)
    private AggregatorType defaultAggregator;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_model", nullable = false, length = 10)
    private EvvSystemModel systemModel;

    @Column(name = "allowed_verification_methods", nullable = false, columnDefinition = "TEXT")
    private String allowedVerificationMethods;

    @Column(name = "gps_tolerance_miles", precision = 5, scale = 2)
    private BigDecimal gpsToleranceMiles;

    @Column(name = "requires_real_time_submission", nullable = false)
    private boolean requiresRealTimeSubmission;

    @Column(name = "manual_entry_cap_percent")
    private Integer manualEntryCapPercent;

    @Column(name = "co_resident_exemption_supported", nullable = false)
    private boolean coResidentExemptionSupported;

    @Column(name = "extra_required_fields", columnDefinition = "TEXT")
    private String extraRequiredFields;

    @Column(name = "compliance_threshold_percent")
    private Integer complianceThresholdPercent;

    @Column(name = "closed_system_acknowledged_by_agency", nullable = false)
    private boolean closedSystemAcknowledgedByAgency;

    protected EvvStateConfig() {}

    public String getStateCode() { return stateCode; }
    public AggregatorType getDefaultAggregator() { return defaultAggregator; }
    public EvvSystemModel getSystemModel() { return systemModel; }
    public String getAllowedVerificationMethods() { return allowedVerificationMethods; }
    public BigDecimal getGpsToleranceMiles() { return gpsToleranceMiles; }
    public boolean isRequiresRealTimeSubmission() { return requiresRealTimeSubmission; }
    public Integer getManualEntryCapPercent() { return manualEntryCapPercent; }
    public boolean isCoResidentExemptionSupported() { return coResidentExemptionSupported; }
    public String getExtraRequiredFields() { return extraRequiredFields; }
    public Integer getComplianceThresholdPercent() { return complianceThresholdPercent; }
    public boolean isClosedSystemAcknowledgedByAgency() { return closedSystemAcknowledgedByAgency; }
}
