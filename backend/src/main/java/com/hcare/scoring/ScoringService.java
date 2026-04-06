package com.hcare.scoring;

import java.util.List;

/**
 * Public surface of the scoring module. The only interface callers outside
 * com.hcare.scoring may use — nothing outside this package queries scoring tables directly.
 *
 * P2 microservice extraction path: swap LocalScoringService for HttpScoringServiceClient,
 * route events to a message broker. No caller changes required.
 */
public interface ScoringService {

    /**
     * Returns eligible caregivers sorted by score descending (AI enabled), or unsorted with
     * score=0 (AI disabled). Returns an empty list if no caregiver passes all hard filters.
     */
    List<RankedCaregiver> rankCandidates(ShiftMatchRequest request);
}
