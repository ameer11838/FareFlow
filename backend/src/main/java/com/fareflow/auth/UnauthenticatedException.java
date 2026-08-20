package com.fareflow.auth;

/** No valid credentials were presented. Mapped to HTTP 401. */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException(String message) {
        super(message);
    }
}
