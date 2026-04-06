package com.hcare.api.v1.clients.dto;

import com.hcare.domain.GoalStatus;
import java.time.LocalDate;

public record UpdateGoalRequest(
    String description,
    LocalDate targetDate,
    GoalStatus status
) {}
