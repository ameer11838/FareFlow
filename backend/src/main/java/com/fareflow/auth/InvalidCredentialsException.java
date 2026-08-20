package com.fareflow.auth;

/**
 * Login failed. Mapped to HTTP 401 with a single generic message, so the response
 * never reveals whether the email exists.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
