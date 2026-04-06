package com.hcare.api.v1.caregivers.dto;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record AvailabilityBlockRequest(
    @NotNull DayOfWeek dayOfWeek,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime
) {}
