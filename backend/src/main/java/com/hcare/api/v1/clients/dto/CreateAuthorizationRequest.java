package com.hcare.api.v1.clients.dto;

import com.hcare.domain.UnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateAuthorizationRequest(
    @NotNull UUID payerId,
    @NotNull UUID serviceTypeId,
    @NotBlank String authNumber,
    @NotNull @Positive BigDecimal authorizedUnits,
    @NotNull UnitType unitType,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate
) {}
