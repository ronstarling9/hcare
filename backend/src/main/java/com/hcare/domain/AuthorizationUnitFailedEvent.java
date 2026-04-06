package com.hcare.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published when all retries to update Authorization.usedUnits after clock-out are exhausted.
 * The clock-out itself is already committed — this event exists so a P2 reconciliation listener
 * can detect and repair the under-reported unit count without losing the visit.
 *
 * TODO(P2): Add AuthorizationUnitFailedEventListener that writes to failed_authorization_unit_updates
 * table for nightly reconciliation job.
 */
public record AuthorizationUnitFailedEvent(
    UUID authorizationId,
    UUID shiftId,
    LocalDateTime timeIn,
    LocalDateTime timeOut
) {}
