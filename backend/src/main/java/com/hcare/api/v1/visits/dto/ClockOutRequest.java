package com.hcare.api.v1.visits.dto;

import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDateTime;

public record ClockOutRequest(
    boolean capturedOffline,
    LocalDateTime deviceCapturedAt
) {
    @AssertTrue(message = "deviceCapturedAt is required when capturedOffline is true")
    public boolean isDeviceCapturedAtProvidedWhenOffline() {
        return !capturedOffline || deviceCapturedAt != null;
    }
}
