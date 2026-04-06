package com.hcare.api.v1.visits.dto;

import java.time.LocalDateTime;

public record ClockOutRequest(
    boolean capturedOffline,
    LocalDateTime deviceCapturedAt
) {}
