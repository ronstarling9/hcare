package com.hcare.integration.evv.exceptions;

public class MissingCredentialsException extends RuntimeException {

    public MissingCredentialsException(String message) {
        super(message);
    }
}
