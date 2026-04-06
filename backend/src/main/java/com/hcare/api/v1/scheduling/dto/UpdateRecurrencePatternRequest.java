package com.hcare.api.v1.scheduling.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record UpdateRecurrencePatternRequest(
    LocalTime scheduledStartTime,
    @Min(1) Integer scheduledDurationMinutes,
    @Pattern(
        regexp = "^\\[\"(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)\"" +
                 "(,\"(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)\")*\\]$",
        message = "daysOfWeek must be a non-empty JSON array of uppercase day names, " +
                  "e.g. [\"MONDAY\",\"WEDNESDAY\"]"
    )
    String daysOfWeek,
    UUID caregiverId,
    UUID authorizationId,
    LocalDate endDate
) {}
