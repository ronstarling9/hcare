package com.hcare.domain;

import java.util.UUID;

/**
 * Published when a shift is cancelled (wired in the future Scheduling API, Plan 6).
 * LocalScoringService listens to increment the cancellation count on CaregiverScoringProfile,
 * keeping cancelRate accurate.
 */
public record ShiftCancelledEvent(
    UUID shiftId,
    UUID caregiverId,
    UUID agencyId
) {}
