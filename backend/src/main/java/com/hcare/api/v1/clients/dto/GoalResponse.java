package com.hcare.api.v1.clients.dto;

import com.hcare.domain.Goal;
import com.hcare.domain.GoalStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record GoalResponse(
    UUID id,
    UUID carePlanId,
    String description,
    LocalDate targetDate,
    GoalStatus status,
    LocalDateTime createdAt
) {
    public static GoalResponse from(Goal g) {
        return new GoalResponse(
            g.getId(), g.getCarePlanId(), g.getDescription(),
            g.getTargetDate(), g.getStatus(), g.getCreatedAt());
    }
}
