package com.hcare.api.v1.clients.dto;

import com.hcare.domain.ClientDiagnosis;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record DiagnosisResponse(
    UUID id,
    UUID clientId,
    String icd10Code,
    String description,
    LocalDate onsetDate,
    LocalDateTime createdAt
) {
    public static DiagnosisResponse from(ClientDiagnosis d) {
        return new DiagnosisResponse(
            d.getId(), d.getClientId(), d.getIcd10Code(),
            d.getDescription(), d.getOnsetDate(), d.getCreatedAt());
    }
}
