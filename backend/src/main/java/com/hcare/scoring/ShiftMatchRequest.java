package com.hcare.scoring;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Input to ScoringService.rankCandidates. authorizationId is nullable — ad-hoc shifts
 * assigned without a payer authorization skip the authorization unit hard filter.
 */
public record ShiftMatchRequest(
    UUID agencyId,
    UUID clientId,
    UUID serviceTypeId,
    UUID authorizationId,
    LocalDateTime scheduledStart,
    LocalDateTime scheduledEnd
) {}
