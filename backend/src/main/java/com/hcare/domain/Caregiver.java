package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "caregivers")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class Caregiver {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String address;

    // Populated at save time via geocoding API — never called at match-request time
    @Column(name = "home_lat", precision = 10, scale = 7)
    private BigDecimal homeLat;

    @Column(name = "home_lng", precision = 10, scale = 7)
    private BigDecimal homeLng;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CaregiverStatus status = CaregiverStatus.ACTIVE;

    @Column(name = "has_pet", nullable = false)
    private boolean hasPet = false;

    // JSON array of language codes e.g. ["en","es"] — parsed at application layer
    @Column(nullable = false, columnDefinition = "TEXT")
    private String languages = "[]";

    @Column(name = "fcm_token", columnDefinition = "TEXT")
    private String fcmToken;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Caregiver() {}

    public Caregiver(UUID agencyId, String firstName, String lastName, String email) {
        this.agencyId = agencyId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    public void setHomeLat(BigDecimal homeLat) { this.homeLat = homeLat; }
    public void setHomeLng(BigDecimal homeLng) { this.homeLng = homeLng; }
    public void setLanguages(String languages) { this.languages = languages; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }

    public UUID getId() { return id; }
    public UUID getAgencyId() { return agencyId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public BigDecimal getHomeLat() { return homeLat; }
    public BigDecimal getHomeLng() { return homeLng; }
    public LocalDate getHireDate() { return hireDate; }
    public CaregiverStatus getStatus() { return status; }
    public boolean isHasPet() { return hasPet; }
    public String getLanguages() { return languages; }
    public String getFcmToken() { return fcmToken; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
