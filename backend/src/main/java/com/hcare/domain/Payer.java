package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payers")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class Payer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "payer_type", nullable = false, length = 20)
    private PayerType payerType;

    @Column(nullable = false, columnDefinition = "CHAR(2)")
    private String state;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Payer() {}

    public Payer(UUID agencyId, String name, PayerType payerType, String state) {
        this.agencyId = agencyId;
        this.name = name;
        this.payerType = payerType;
        this.state = state;
    }

    public UUID getId() { return id; }
    public UUID getAgencyId() { return agencyId; }
    public String getName() { return name; }
    public PayerType getPayerType() { return payerType; }
    public String getState() { return state; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
