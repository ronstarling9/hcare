package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "communication_messages")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class CommunicationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    // Polymorphic participant types: AGENCY_USER | CAREGIVER | FAMILY_PORTAL_USER — no FK constraint
    @Column(name = "sender_type", nullable = false, length = 30)
    private String senderType;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "recipient_type", nullable = false, length = 30)
    private String recipientType;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    // sentAt is both the user-visible send time and the row creation time —
    // messages are immutable so there is no useful distinction.
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt = LocalDateTime.now();

    protected CommunicationMessage() {}

    public CommunicationMessage(UUID agencyId, String senderType, UUID senderId,
                                 String recipientType, UUID recipientId, String body) {
        this.agencyId = agencyId;
        this.senderType = senderType;
        this.senderId = senderId;
        this.recipientType = recipientType;
        this.recipientId = recipientId;
        this.body = body;
    }

    public void setSubject(String subject) { this.subject = subject; }

    public UUID getId() { return id; }
    public UUID getAgencyId() { return agencyId; }
    public String getSenderType() { return senderType; }
    public UUID getSenderId() { return senderId; }
    public String getRecipientType() { return recipientType; }
    public UUID getRecipientId() { return recipientId; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public LocalDateTime getSentAt() { return sentAt; }
}
