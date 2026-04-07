package com.hcare.api.v1.documents.dto;

import com.hcare.domain.Document;
import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentResponse(
    UUID id,
    UUID ownerId,
    String fileName,
    String documentType,
    UUID uploadedBy,
    LocalDateTime uploadedAt
) {
    public static DocumentResponse from(Document d) {
        return new DocumentResponse(d.getId(), d.getOwnerId(), d.getFileName(),
            d.getDocumentType(), d.getUploadedBy(), d.getUploadedAt());
    }
}
