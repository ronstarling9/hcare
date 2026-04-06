package com.hcare.api.v1.scheduling.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignCaregiverRequest(
    @NotNull UUID caregiverId
) {}
