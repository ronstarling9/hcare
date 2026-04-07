package com.hcare.api.v1.caregivers.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record AvailabilityBlockRequest(
    @NotNull DayOfWeek dayOfWeek,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime
) {
    @AssertTrue(message = "endTime must be after startTime")
    private boolean isEndTimeAfterStartTime() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }
}
