package com.hcare.exception;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public record ErrorResponse(
    String message,
    int status,
    LocalDateTime timestamp
) {
    public static ErrorResponse of(String message, int status) {
        return new ErrorResponse(message, status, LocalDateTime.now(ZoneOffset.UTC));
    }
}
