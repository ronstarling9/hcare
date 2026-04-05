package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "clients")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(columnDefinition = "TEXT")
    private String address;

    // Geocoded at save time — never called per match request
    @Column(precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(precision = 10, scale = 7)
    private BigDecimal lng;

    @Column(length = 20)
    private String phone;

    @Column(name = "medicaid_id", length = 50)
    private String medicaidId;

    // Overrides agency.state for EVV routing. Null = use agency state.
    // Required for border-county agencies serving clients in two states.
    @Column(name = "service_state", columnDefinition = "CHAR(2)")
    private String serviceState;

    @Column(name = "preferred_caregiver_gender", length = 10)
    private String preferredCaregiverGender;

    // JSON array of language codes — parsed at application layer
    @Column(name = "preferred_languages", nullable = false, columnDefinition = "TEXT")
    private String preferredLanguages = "[]";

    @Column(name = "no_pet_caregiver", nullable = false)
    private boolean noPetCaregiver = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClientStatus status = ClientStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Client() {}

    public Client(UUID agencyId, String firstName, String lastName, LocalDate dateOfBirth) {
        this.agencyId = agencyId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
    }

    public void setServiceState(String serviceState) { this.serviceState = serviceState; }
    public void setLat(BigDecimal lat) { this.lat = lat; }
    public void setLng(BigDecimal lng) { this.lng = lng; }
    public void setPreferredLanguages(String preferredLanguages) { this.preferredLanguages = preferredLanguages; }
    public void setNoPetCaregiver(boolean noPetCaregiver) { this.noPetCaregiver = noPetCaregiver; }
    public void setMedicaidId(String medicaidId) { this.medicaidId = medicaidId; }

    public UUID getId() { return id; }
    public UUID getAgencyId() { return agencyId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getAddress() { return address; }
    public BigDecimal getLat() { return lat; }
    public BigDecimal getLng() { return lng; }
    public String getPhone() { return phone; }
    public String getMedicaidId() { return medicaidId; }
    public String getServiceState() { return serviceState; }
    public String getPreferredCaregiverGender() { return preferredCaregiverGender; }
    public String getPreferredLanguages() { return preferredLanguages; }
    public boolean isNoPetCaregiver() { return noPetCaregiver; }
    public ClientStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
