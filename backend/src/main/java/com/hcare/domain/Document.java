package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 20)
    private DocumentOwnerType ownerType;

    // Polymorphic FK — references client or caregiver id. No DB-level FK constraint.
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_path", nullable = false, columnDefinition = "TEXT")
    private String filePath;

    @Column(name = "document_type", length = 100)
    private String documentType;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    protected Document() {}

    public Document(UUID agencyId, DocumentOwnerType ownerType, UUID ownerId,
                    String fileName, String filePath) {
        this.agencyId = agencyId;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.fileName = fileName;
        this.filePath = filePath;
    }

    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public void setUploadedBy(UUID uploadedBy) { this.uploadedBy = uploadedBy; }

    public UUID getId() { return id; }
    public UUID getAgencyId() { return agencyId; }
    public DocumentOwnerType getOwnerType() { return ownerType; }
    public UUID getOwnerId() { return ownerId; }
    public String getFileName() { return fileName; }
    public String getFilePath() { return filePath; }
    public String getDocumentType() { return documentType; }
    public UUID getUploadedBy() { return uploadedBy; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
}
