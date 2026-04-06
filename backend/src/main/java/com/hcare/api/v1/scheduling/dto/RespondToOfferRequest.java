package com.hcare.api.v1.scheduling.dto;

import com.hcare.domain.ShiftOfferResponse;
import jakarta.validation.constraints.NotNull;

public record RespondToOfferRequest(
    @NotNull ShiftOfferResponse response
) {}
