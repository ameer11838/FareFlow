package com.fareflow.session;

/** Pre-trip range under FareFlow's simulated usage model. */
public record UsageFareEstimate(
        long minimumCents,
        long maximumCents,
        int plannedStops,
        long plannedDistanceMetres,
        String pricingVersion
) {
}
