package com.fareflow.fare.rules;

/**
 * A ceiling on what a rider pays across a period.
 *
 * <p>Once the cap is reached further rides are free, which is how OMNY and Oyster
 * behave. Modelled here so the engine can answer "what will this ride actually
 * cost me" rather than "what is this ride's list price".
 */
public record FareCap(String agency, Period period, long capCents, String label) {

    public enum Period { DAILY, WEEKLY }

    public FareCap {
        if (capCents <= 0) {
            throw new IllegalArgumentException("A fare cap must be positive");
        }
    }

    /**
     * How much of {@code proposedCents} is actually chargeable given prior spend.
     *
     * @return the chargeable amount, never negative and never more than proposed
     */
    public long chargeableCents(long alreadySpentCents, long proposedCents) {
        long remainingHeadroom = Math.max(0, capCents - alreadySpentCents);
        return Math.min(proposedCents, remainingHeadroom);
    }
}
