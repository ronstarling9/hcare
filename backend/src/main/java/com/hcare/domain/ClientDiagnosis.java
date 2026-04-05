package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_diagnoses")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class ClientDiagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "icd10_code", nullable = false, length = 10)
    private String icd10Code;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "onset_date")
    private LocalDate onsetDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected ClientDiagnosis() {}

    public ClientDiagnosis(UUID clientId, UUID agencyId, String icd10Code) {
        this.clientId = clientId;
        this.agencyId = agencyId;
        this.icd10Code = icd10Code;
    }

    public UUID getId() { return id; }
    public UUID getClientId() { return clientId; }
    public UUID getAgencyId() { return agencyId; }
    public String getIcd10Code() { return icd10Code; }
    public String getDescription() { return description; }
    public LocalDate getOnsetDate() { return onsetDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
