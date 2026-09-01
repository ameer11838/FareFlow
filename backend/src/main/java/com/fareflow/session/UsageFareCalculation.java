package com.fareflow.session;

import java.util.List;

/** Integer-cent result from FareFlow's simulated usage model. */
public record UsageFareCalculation(
        long totalCents,
        long baseCents,
        long distanceCents,
        long stopCents,
        long transferDiscountCents,
        long concessionDiscountCents,
        long capDiscountCents,
        long distanceMetres,
        int stops,
        int progressUnits,
        String pricingVersion,
        List<String> breakdown
) {
}
