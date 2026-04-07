package com.hcare.api.v1.clients.dto;

import com.hcare.domain.GoalStatus;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateGoalRequest(
    @Size(min = 1) String description,
    LocalDate targetDate,
    GoalStatus status
) {}
