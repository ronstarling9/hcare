package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "background_checks")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class BackgroundCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "caregiver_id", nullable = false)
    private UUID caregiverId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_type", nullable = false, length = 30)
    private BackgroundCheckType checkType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BackgroundCheckResult result;

    @Column(name = "checked_at", nullable = false)
    private LocalDate checkedAt;

    @Column(name = "renewal_due_date")
    private LocalDate renewalDueDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected BackgroundCheck() {}

    public BackgroundCheck(UUID caregiverId, UUID agencyId, BackgroundCheckType checkType,
                           BackgroundCheckResult result, LocalDate checkedAt) {
        this.caregiverId = caregiverId;
        this.agencyId = agencyId;
        this.checkType = checkType;
        this.result = result;
        this.checkedAt = checkedAt;
    }

    public void setRenewalDueDate(LocalDate renewalDueDate) { this.renewalDueDate = renewalDueDate; }

    public UUID getId() { return id; }
    public UUID getCaregiverId() { return caregiverId; }
    public UUID getAgencyId() { return agencyId; }
    public BackgroundCheckType getCheckType() { return checkType; }
    public BackgroundCheckResult getResult() { return result; }
    public LocalDate getCheckedAt() { return checkedAt; }
    public LocalDate getRenewalDueDate() { return renewalDueDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
