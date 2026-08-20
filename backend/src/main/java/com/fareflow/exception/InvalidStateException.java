package com.fareflow.exception;

/**
 * Thrown when a request is well-formed but conflicts with the current state of a
 * resource — cancelling an already-cancelled trip, for example. Mapped to HTTP 409.
 */
public class InvalidStateException extends RuntimeException {

    public InvalidStateException(String message) {
        super(message);
    }
}
