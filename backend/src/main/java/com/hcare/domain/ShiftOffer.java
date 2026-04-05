package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "shift_offers",
    uniqueConstraints = @UniqueConstraint(name = "uq_shift_offers", columnNames = {"shift_id", "caregiver_id"}))
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class ShiftOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "shift_id", nullable = false)
    private UUID shiftId;

    @Column(name = "caregiver_id", nullable = false)
    private UUID caregiverId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "offered_at", nullable = false)
    private LocalDateTime offeredAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShiftOfferResponse response = ShiftOfferResponse.NO_RESPONSE;

    protected ShiftOffer() {}

    public ShiftOffer(UUID shiftId, UUID caregiverId, UUID agencyId) {
        this.shiftId = shiftId;
        this.caregiverId = caregiverId;
        this.agencyId = agencyId;
        this.offeredAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void respond(ShiftOfferResponse response) {
        this.response = response;
        this.respondedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() { return id; }
    public UUID getShiftId() { return shiftId; }
    public UUID getCaregiverId() { return caregiverId; }
    public UUID getAgencyId() { return agencyId; }
    public LocalDateTime getOfferedAt() { return offeredAt; }
    public LocalDateTime getRespondedAt() { return respondedAt; }
    public ShiftOfferResponse getResponse() { return response; }
}
