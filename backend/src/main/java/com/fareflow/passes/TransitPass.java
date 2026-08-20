package com.fareflow.passes;

/**
 * A purchasable pass, priced from a published tariff.
 *
 * @param coversAgency which agency's fares the pass covers
 * @param validDays    how many days the pass covers, used to normalise to a week
 */
public record TransitPass(
        String code,
        String name,
        String coversAgency,
        Period period,
        int validDays,
        long priceCents
) {

    public enum Period { DAILY, WEEKLY, MONTHLY }

    public TransitPass {
        if (priceCents <= 0) {
            throw new IllegalArgumentException("A pass must have a positive price");
        }
        if (validDays <= 0) {
            throw new IllegalArgumentException("A pass must cover at least one day");
        }
    }

    /** Cost per week, so passes of different lengths can be compared like for like. */
    public double weeklyEquivalentCents() {
        return priceCents * (7.0 / validDays);
    }
}
