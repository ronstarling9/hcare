package com.hcare.api.v1.scheduling.dto;

import com.hcare.domain.ShiftOfferResponse;
import java.time.LocalDateTime;
import java.util.UUID;

public record ShiftOfferSummary(
    UUID id,
    UUID shiftId,
    UUID caregiverId,
    UUID agencyId,
    LocalDateTime offeredAt,
    LocalDateTime respondedAt,
    ShiftOfferResponse response
) {}
