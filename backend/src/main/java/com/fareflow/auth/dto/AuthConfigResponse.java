package com.fareflow.auth.dto;

/**
 * Tells the client which mode the server is running in, so the frontend does not
 * have to guess from its own build-time flag and risk disagreeing with the backend.
 */
public record AuthConfigResponse(boolean authEnabled, boolean demoMode, String demoUserName) {
}
