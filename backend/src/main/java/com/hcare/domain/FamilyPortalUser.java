package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

// Authentication model: magic link via email — no password stored.
// JWT claim on successful auth: {"role":"FAMILY_PORTAL","clientId":"...","agencyId":"..."}.
// The clientId JWT claim is the hard scope boundary — all /api/v1/family/ endpoints
// verify this claim and reject requests for resources belonging to other clients.
@Entity
@Table(name = "family_portal_users")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class FamilyPortalUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(nullable = false)
    private String email;

    @Column(length = 255)
    private String name;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);

    protected FamilyPortalUser() {}

    public FamilyPortalUser(UUID clientId, UUID agencyId, String email) {
        this.clientId = clientId;
        this.agencyId = agencyId;
        this.email = email;
    }

    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() { return id; }
    public UUID getClientId() { return clientId; }
    public UUID getAgencyId() { return agencyId; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
