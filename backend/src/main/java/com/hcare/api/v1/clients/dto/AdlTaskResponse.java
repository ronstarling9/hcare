package com.hcare.api.v1.clients.dto;

import com.hcare.domain.AdlTask;
import com.hcare.domain.AssistanceLevel;
import java.time.LocalDateTime;
import java.util.UUID;

public record AdlTaskResponse(
    UUID id,
    UUID carePlanId,
    String name,
    AssistanceLevel assistanceLevel,
    String instructions,
    String frequency,
    int sortOrder,
    LocalDateTime createdAt
) {
    public static AdlTaskResponse from(AdlTask t) {
        return new AdlTaskResponse(
            t.getId(), t.getCarePlanId(), t.getName(), t.getAssistanceLevel(),
            t.getInstructions(), t.getFrequency(), t.getSortOrder(), t.getCreatedAt());
    }
}
