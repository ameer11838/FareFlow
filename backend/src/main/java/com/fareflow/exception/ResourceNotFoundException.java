package com.fareflow.exception;

/** Thrown when a requested entity does not exist. Mapped to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
