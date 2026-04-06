package com.hcare.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by VisitService.clockOut via ApplicationEventPublisher after the outer
 * transaction commits. LocalScoringService listens via @TransactionalEventListener to update
 * CaregiverScoringProfile and CaregiverClientAffinity asynchronously.
 *
 * timeIn / timeOut are the authoritative EVV timestamps — deviceCapturedAt for offline
 * visits, server receipt time for online visits (same values stored on EvvRecord).
 */
public record ShiftCompletedEvent(
    UUID shiftId,
    UUID caregiverId,
    UUID clientId,
    UUID agencyId,
    LocalDateTime timeIn,
    LocalDateTime timeOut
) {}
