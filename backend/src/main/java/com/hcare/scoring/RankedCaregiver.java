package com.hcare.scoring;

import java.util.UUID;

/**
 * A caregiver who passed all hard filters, with an optional weighted score and explanation.
 *
 * When featureFlags.aiSchedulingEnabled = false (Starter tier), score is 0.0 and explanation
 * is null — candidates are eligible but not ranked. The caller presents them in undefined order.
 */
public record RankedCaregiver(
    UUID caregiverId,
    double score,
    String explanation
) {}
