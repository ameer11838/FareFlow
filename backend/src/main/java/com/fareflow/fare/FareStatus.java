package com.fareflow.fare;

/**
 * How much to trust a fare.
 *
 * <p>The distinction that matters: {@link #UNKNOWN} must never be rendered as
 * $0.00, and must never let a journey win on price simply because nobody could
 * price it. The optimization adapter treats unknown fares explicitly.
 */
public enum FareStatus {
    /** Priced from a published rule with no ambiguity — a flat PATH or subway fare. */
    EXACT,
    /** Priced from a rule that depends on inputs we approximated, such as a zone guess. */
    ESTIMATED,
    /** Genuinely not knowable here — dynamic pricing like Amtrak. Never guessed. */
    UNKNOWN
}
