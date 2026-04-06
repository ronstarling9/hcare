package com.hcare.api.v1.scheduling.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record UpdateRecurrencePatternRequest(
    LocalTime scheduledStartTime,
    @Min(1) Integer scheduledDurationMinutes,
    @Pattern(regexp = SchedulingValidation.DAYS_OF_WEEK_REGEXP,
             message = SchedulingValidation.DAYS_OF_WEEK_MESSAGE)
    String daysOfWeek,
    UUID caregiverId,
    UUID authorizationId,
    LocalDate endDate
) {}
