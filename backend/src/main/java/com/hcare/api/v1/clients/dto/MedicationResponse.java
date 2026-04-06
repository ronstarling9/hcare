package com.hcare.api.v1.clients.dto;

import com.hcare.domain.ClientMedication;
import java.time.LocalDateTime;
import java.util.UUID;

public record MedicationResponse(
    UUID id,
    UUID clientId,
    String name,
    String dosage,
    String route,
    String schedule,
    String prescriber,
    LocalDateTime createdAt
) {
    public static MedicationResponse from(ClientMedication m) {
        return new MedicationResponse(
            m.getId(), m.getClientId(), m.getName(), m.getDosage(),
            m.getRoute(), m.getSchedule(), m.getPrescriber(), m.getCreatedAt());
    }
}
