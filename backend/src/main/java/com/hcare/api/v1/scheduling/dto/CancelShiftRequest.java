package com.hcare.api.v1.scheduling.dto;

import jakarta.validation.constraints.Size;

public record CancelShiftRequest(
    // @Valid must be present at the call site for this constraint to be enforced
    @Size(max = 1000)
    String notes
) {}
