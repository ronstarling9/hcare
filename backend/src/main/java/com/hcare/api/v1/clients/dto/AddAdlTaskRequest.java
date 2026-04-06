package com.hcare.api.v1.clients.dto;

import com.hcare.domain.AssistanceLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddAdlTaskRequest(
    @NotBlank String name,
    @NotNull AssistanceLevel assistanceLevel,
    String instructions,
    String frequency,
    Integer sortOrder
) {}
