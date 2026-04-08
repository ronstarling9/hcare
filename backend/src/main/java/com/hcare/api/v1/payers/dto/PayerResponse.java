package com.hcare.api.v1.payers.dto;

import com.hcare.domain.PayerType;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for a single payer.
 *
 * <p>evvAggregator is derived at query time from EvvStateConfig for the payer's state.
 * It is null when no EvvStateConfig row exists for the state (uncommon — states are
 * Flyway-seeded, but possible for custom/test payers with unknown state codes).
 */
public record PayerResponse(
    UUID id,
    String name,
    PayerType payerType,
    String state,
    String evvAggregator,
    LocalDateTime createdAt
) {}
