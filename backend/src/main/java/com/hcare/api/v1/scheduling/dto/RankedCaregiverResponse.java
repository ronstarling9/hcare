package com.hcare.api.v1.scheduling.dto;

import java.util.UUID;

public record RankedCaregiverResponse(
    UUID caregiverId,
    double score,
    String explanation
) {}
