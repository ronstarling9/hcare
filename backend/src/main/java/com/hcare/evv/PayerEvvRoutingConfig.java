package com.hcare.evv;

import com.hcare.domain.PayerType;
import jakarta.persistence.*;
import java.util.UUID;

// Global reference data — no agencyId, no @Filter.
// Rows cover multi-aggregator states: NY (3 aggregators), FL/NC/VA/TN/AR (MCO-specific mappings).
// Aggregator selection: check this table first, then fall back to EvvStateConfig.defaultAggregator.
@Entity
@Table(name = "payer_evv_routing_configs")
public class PayerEvvRoutingConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "state_code", nullable = false, columnDefinition = "CHAR(2)")
    private String stateCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "payer_type", nullable = false, length = 20)
    private PayerType payerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregator_type", nullable = false, length = 30)
    private AggregatorType aggregatorType;

    @Column(columnDefinition = "TEXT")
    private String notes;

    protected PayerEvvRoutingConfig() {}

    public PayerEvvRoutingConfig(String stateCode, PayerType payerType, AggregatorType aggregatorType) {
        this.stateCode = stateCode;
        this.payerType = payerType;
        this.aggregatorType = aggregatorType;
    }

    public UUID getId() { return id; }
    public String getStateCode() { return stateCode; }
    public PayerType getPayerType() { return payerType; }
    public AggregatorType getAggregatorType() { return aggregatorType; }
    public String getNotes() { return notes; }
}
