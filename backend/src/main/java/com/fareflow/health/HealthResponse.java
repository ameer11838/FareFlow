package com.fareflow.health;

/**
 * Response body for {@code GET /api/health}.
 *
 * <p>A record rather than a Map so the JSON shape is defined by the type system.
 */
public record HealthResponse(String status) {

    public static HealthResponse up() {
        return new HealthResponse("UP");
    }
}
