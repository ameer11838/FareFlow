package com.fareflow.auth;

/**
 * Authenticated, but not allowed to touch this resource. Mapped to HTTP 403.
 *
 * <p>Distinct from {@link UnauthenticatedException} on purpose: 401 means "log in",
 * 403 means "logging in again will not help".
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
