package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_medications")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class ClientMedication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(nullable = false)
    private String name;

    @Column(length = 100)
    private String dosage;

    @Column(length = 100)
    private String route;

    @Column(columnDefinition = "TEXT")
    private String schedule;

    @Column(length = 255)
    private String prescriber;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected ClientMedication() {}

    public ClientMedication(UUID clientId, UUID agencyId, String name) {
        this.clientId = clientId;
        this.agencyId = agencyId;
        this.name = name;
    }

    public UUID getId() { return id; }
    public UUID getClientId() { return clientId; }
    public UUID getAgencyId() { return agencyId; }
    public String getName() { return name; }
    public String getDosage() { return dosage; }
    public String getRoute() { return route; }
    public String getSchedule() { return schedule; }
    public String getPrescriber() { return prescriber; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
