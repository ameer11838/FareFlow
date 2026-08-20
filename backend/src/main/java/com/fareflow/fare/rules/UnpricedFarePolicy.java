package com.fareflow.fare.rules;

import com.fareflow.journey.JourneyLeg;

import java.util.Optional;

/**
 * A policy for services FareFlow deliberately refuses to price.
 *
 * <p>Amtrak is the case that matters: its fares are dynamic and yield-managed, so
 * any number FareFlow produced would be fiction. Returning empty propagates
 * {@code UNKNOWN} all the way to the UI, which shows "Fare not available" instead
 * of a made-up figure.
 *
 * <p>This class exists so that "we cannot price this" is a modelled outcome rather
 * than a missing case.
 */
public final class UnpricedFarePolicy implements FarePolicy {

    private final String code;
    private final String agency;
    private final String reason;

    public UnpricedFarePolicy(String code, String agency, String reason) {
        this.code = code;
        this.agency = agency;
        this.reason = reason;
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
        return Optional.empty();
    }

    @Override
    public String describe(JourneyLeg leg) {
        return "%s — %s".formatted(leg.lineName(), reason);
    }
}
