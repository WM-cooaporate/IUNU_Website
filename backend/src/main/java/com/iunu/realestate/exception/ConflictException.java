package com.iunu.realestate.exception;

/**
 * The request is well formed but cannot be served in the resource's current
 * state - "this job is already running", not "you got something wrong".
 * Mapped to 409 by {@link GlobalExceptionHandler}.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
