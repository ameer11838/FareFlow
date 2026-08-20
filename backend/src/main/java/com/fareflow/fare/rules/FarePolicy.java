package com.fareflow.fare.rules;

import com.fareflow.journey.JourneyLeg;

/**
 * Prices a single transit leg under one agency's published tariff.
 *
 * <p>One implementation per pricing model, not per agency: PATH and the subway are
 * both flat-fare, so they share {@link FlatFarePolicy} with different amounts.
 * Adding an agency is data; adding a <em>pricing model</em> is a class.
 */
public interface FarePolicy {

    /** Matches {@code transit_lines.fare_policy}. */
    String code();

    String agency();

    /**
     * The fare for this leg in isolation, before transfer credits or caps.
     *
     * <p>Empty when this policy genuinely cannot price the leg — dynamic pricing,
     * for instance. Returning empty is a real answer, and is what keeps a guess
     * from entering the system.
     */
    java.util.Optional<Long> baseFareCents(JourneyLeg leg);

    /** Human label used in the fare breakdown. */
    String describe(JourneyLeg leg);
}
