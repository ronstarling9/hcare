package com.hcare.api.v1.visits.dto;

import com.hcare.evv.VerificationMethod;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ClockInRequest(
    @NotNull BigDecimal lat,
    @NotNull BigDecimal lon,
    @NotNull VerificationMethod verificationMethod,
    boolean capturedOffline,
    LocalDateTime deviceCapturedAt
) {
    @AssertTrue(message = "deviceCapturedAt is required when capturedOffline is true")
    public boolean isDeviceCapturedAtProvidedWhenOffline() {
        return !capturedOffline || deviceCapturedAt != null;
    }
}
