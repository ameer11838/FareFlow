package com.fareflow.fare.rules;

import com.fareflow.journey.JourneyLeg;

import java.util.Optional;

/**
 * A single fare regardless of distance — PATH, the NYC subway, Newark Light Rail.
 *
 * <p>Fares here are published tariffs, not estimates, so legs priced by this policy
 * are {@code EXACT}.
 */
public final class FlatFarePolicy implements FarePolicy {

    private final String code;
    private final String agency;
    private final long fareCents;
    private final String label;

    public FlatFarePolicy(String code, String agency, long fareCents, String label) {
        if (fareCents < 0) {
            throw new IllegalArgumentException("A flat fare must not be negative");
        }
        this.code = code;
        this.agency = agency;
        this.fareCents = fareCents;
        this.label = label;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String agency() {
        return agency;
    }

    @Override
    public Optional<Long> baseFareCents(JourneyLeg leg) {
        return Optional.of(fareCents);
    }

    @Override
    public String describe(JourneyLeg leg) {
        return label;
    }
}
