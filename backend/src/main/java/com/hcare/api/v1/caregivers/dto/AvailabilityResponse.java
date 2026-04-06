package com.hcare.api.v1.caregivers.dto;

import com.hcare.domain.CaregiverAvailability;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record AvailabilityResponse(
    UUID caregiverId,
    List<AvailabilityBlock> blocks
) {
    public record AvailabilityBlock(
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime
    ) {
        public static AvailabilityBlock from(CaregiverAvailability a) {
            return new AvailabilityBlock(a.getDayOfWeek(), a.getStartTime(), a.getEndTime());
        }
    }
}
