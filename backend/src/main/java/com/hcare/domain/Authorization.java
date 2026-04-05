package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "authorizations")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class Authorization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "payer_id", nullable = false)
    private UUID payerId;

    @Column(name = "service_type_id", nullable = false)
    private UUID serviceTypeId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "auth_number", nullable = false, length = 100)
    private String authNumber;

    @Column(name = "authorized_units", nullable = false, precision = 10, scale = 2)
    private BigDecimal authorizedUnits;

    @Column(name = "used_units", nullable = false, precision = 10, scale = 2)
    private BigDecimal usedUnits = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false, length = 10)
    private UnitType unitType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    // JPA @Version: Hibernate emits UPDATE ... WHERE id=? AND version=?
    // If 0 rows are updated (another transaction already incremented the version),
    // Hibernate throws StaleObjectStateException → Spring wraps as
    // ObjectOptimisticLockingFailureException. The caller retries the operation.
    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Authorization() {}

    public Authorization(UUID clientId, UUID payerId, UUID serviceTypeId, UUID agencyId,
                         String authNumber, BigDecimal authorizedUnits,
                         UnitType unitType, LocalDate startDate, LocalDate endDate) {
        this.clientId = clientId;
        this.payerId = payerId;
        this.serviceTypeId = serviceTypeId;
        this.agencyId = agencyId;
        this.authNumber = authNumber;
        this.authorizedUnits = authorizedUnits;
        this.usedUnits = BigDecimal.ZERO;
        this.unitType = unitType;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void addUsedUnits(BigDecimal amount) {
        this.usedUnits = this.usedUnits.add(amount);
    }

    public UUID getId() { return id; }
    public UUID getClientId() { return clientId; }
    public UUID getPayerId() { return payerId; }
    public UUID getServiceTypeId() { return serviceTypeId; }
    public UUID getAgencyId() { return agencyId; }
    public String getAuthNumber() { return authNumber; }
    public BigDecimal getAuthorizedUnits() { return authorizedUnits; }
    public BigDecimal getUsedUnits() { return usedUnits; }
    public UnitType getUnitType() { return unitType; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
