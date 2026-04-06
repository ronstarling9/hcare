package com.hcare.api.v1.scheduling.dto;

import com.hcare.domain.ShiftStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record ShiftSummaryResponse(
    UUID id,
    UUID agencyId,
    UUID clientId,
    UUID caregiverId,
    UUID serviceTypeId,
    UUID authorizationId,
    UUID sourcePatternId,
    LocalDateTime scheduledStart,
    LocalDateTime scheduledEnd,
    ShiftStatus status,
    String notes
) {}
