package com.hcare.api.v1.scheduling.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;

public record CreateShiftRequest(
    @NotNull UUID clientId,
    UUID caregiverId,
    @NotNull UUID serviceTypeId,
    UUID authorizationId,
    @NotNull LocalDateTime scheduledStart,
    @NotNull LocalDateTime scheduledEnd,
    String notes
) {}
