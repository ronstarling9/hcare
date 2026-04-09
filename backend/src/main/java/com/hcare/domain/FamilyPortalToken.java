package com.hcare.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * One-time invite token for the family portal.
 * tokenHash is hex-encoded SHA-256(rawToken). The raw token only exists in the invite URL
 * and is never stored. When a family member clicks the link, the backend recomputes the
 * hash and looks up this row — then deletes it (one-time use).
 */
@Entity
@Table(name = "family_portal_tokens")
public class FamilyPortalToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "fpu_id", nullable = false)
    private UUID fpuId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);

    protected FamilyPortalToken() {}

    public FamilyPortalToken(String tokenHash, UUID fpuId, UUID clientId,
                              UUID agencyId, LocalDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.fpuId = fpuId;
        this.clientId = clientId;
        this.agencyId = agencyId;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public UUID getFpuId() { return fpuId; }
    public UUID getClientId() { return clientId; }
    public UUID getAgencyId() { return agencyId; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
