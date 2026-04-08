package com.hcare.api.v1.scheduling.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * All fields are optional — only non-null values are applied (PATCH semantics).
 * caregiverId may be explicitly set to null via the JSON literal {@code null}
 * to unassign the caregiver; omitting the field leaves the caregiver unchanged.
 * Use a wrapper type (Boolean sentinel pattern) is not needed here because
 * Java records allow null to represent "not provided" for UUID/String fields,
 * and LocalDateTime null is equally unambiguous.
 */
public record UpdateShiftRequest(
    UUID clientId,
    UUID caregiverId,
    UUID serviceTypeId,
    UUID authorizationId,
    LocalDateTime scheduledStart,
    LocalDateTime scheduledEnd,
    String notes
) {}
