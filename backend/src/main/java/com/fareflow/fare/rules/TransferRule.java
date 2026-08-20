package com.fareflow.fare.rules;

/**
 * A credit applied when a rider changes between two agencies within a journey.
 *
 * <p>Real networks do this constantly — a free bus-to-subway transfer, a discounted
 * commuter-rail-to-transit connection. Modelling it as data rather than an
 * {@code if} in the engine means a new agreement is a new row, not a code change.
 *
 * @param creditCents how much comes off, capped at the second leg's fare so a
 *                    transfer can never make a journey cheaper than free
 */
public record TransferRule(String fromAgency, String toAgency, long creditCents, String label) {

    public TransferRule {
        if (creditCents < 0) {
            throw new IllegalArgumentException("A transfer credit must not be negative");
        }
    }

    public boolean matches(String from, String to) {
        return fromAgency.equals(from) && toAgency.equals(to);
    }
}
