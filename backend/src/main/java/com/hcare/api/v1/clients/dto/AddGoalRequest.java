package com.hcare.api.v1.clients.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record AddGoalRequest(
    @NotBlank String description,
    LocalDate targetDate
) {}
