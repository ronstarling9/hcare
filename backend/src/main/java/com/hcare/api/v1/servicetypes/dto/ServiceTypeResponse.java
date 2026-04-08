package com.hcare.api.v1.servicetypes.dto;

import java.util.List;
import java.util.UUID;

/**
 * DTO for a single service type.
 *
 * <p>{@code requiredCredentials} is never null — it is an empty list when the
 * underlying JSON column is absent, empty, or unparseable. Values are credential
 * type identifiers (e.g. "CPR", "FIRST_AID"), not display names.
 */
public record ServiceTypeResponse(
    UUID id,
    String name,
    String code,
    boolean requiresEvv,
    List<String> requiredCredentials
) {}
