package com.hcare.integration.billing.validation;

/**
 * Thrown by a {@link ClaimValidationHandler} when a claim fails a validation rule.
 */
public class ClaimValidationException extends RuntimeException {

    public ClaimValidationException(String message) {
        super(message);
    }

    public ClaimValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
