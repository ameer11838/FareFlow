package com.fareflow.recommendation.optimization;

/**
 * A route as the scoring engine sees it.
 *
 * <p>Deliberately not the JPA entity. Keeping a plain value type here is what lets
 * the whole optimization package stay free of Spring, Hibernate, and the database,
 * so every scoring class can be instantiated directly in a plain JUnit test.
 *
 * @param fareCents fare in integer cents — never a floating point amount
 */
public record RouteCandidate(
        long routeId,
        String provider,
        String providerDisplayName,
        String mode,
        int durationMinutes,
        long fareCents,
        int transfers
) {

    public RouteCandidate {
        if (providerDisplayName == null || providerDisplayName.isBlank()) {
            throw new IllegalArgumentException("providerDisplayName must not be blank");
        }
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("durationMinutes must be positive but was " + durationMinutes);
        }
        if (fareCents < 0) {
            throw new IllegalArgumentException("fareCents must not be negative but was " + fareCents);
        }
        if (transfers < 0) {
            throw new IllegalArgumentException("transfers must not be negative but was " + transfers);
        }
    }
}
