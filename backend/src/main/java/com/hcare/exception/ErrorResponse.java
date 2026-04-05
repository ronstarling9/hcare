package com.hcare.exception;

import java.time.LocalDateTime;

public record ErrorResponse(
    String message,
    int status,
    LocalDateTime timestamp
) {
    public static ErrorResponse of(String message, int status) {
        return new ErrorResponse(message, status, LocalDateTime.now());
    }
}
