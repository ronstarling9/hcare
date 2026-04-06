package com.hcare.api.v1.scheduling.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public record RecurrencePatternResponse(
    UUID id,
    UUID agencyId,
    UUID clientId,
    UUID caregiverId,
    UUID serviceTypeId,
    UUID authorizationId,
    LocalTime scheduledStartTime,
    int scheduledDurationMinutes,
    String daysOfWeek,
    LocalDate startDate,
    LocalDate endDate,
    LocalDate generatedThrough,
    boolean active,
    Long version,
    LocalDateTime createdAt
) {}
