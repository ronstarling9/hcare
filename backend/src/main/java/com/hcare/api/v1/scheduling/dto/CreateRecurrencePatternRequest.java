package com.hcare.api.v1.scheduling.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreateRecurrencePatternRequest(
    @NotNull UUID clientId,
    UUID caregiverId,
    @NotNull UUID serviceTypeId,
    UUID authorizationId,
    @NotNull LocalTime scheduledStartTime,
    @Min(1) int scheduledDurationMinutes,
    @NotBlank
    @Pattern(
        regexp = "^\\[\"(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)\"" +
                 "(,\"(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)\")*\\]$",
        message = "daysOfWeek must be a non-empty JSON array of uppercase day names, " +
                  "e.g. [\"MONDAY\",\"WEDNESDAY\"]"
    )
    String daysOfWeek,
    @NotNull LocalDate startDate,
    LocalDate endDate
) {}
