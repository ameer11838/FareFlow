package com.fareflow.recommendation.optimization;

/**
 * Labels a route can carry in a recommendation. A single route may carry several
 * — the cheapest option is often also the slowest, and with one candidate route
 * that route is simultaneously cheapest, fastest, and best value.
 */
public enum RecommendationLabel {

    /** Lowest fare in the candidate set. */
    CHEAPEST,

    /** Shortest travel time in the candidate set. */
    FASTEST,

    /** Lowest weighted score — the best balance under the active weights. */
    BEST_VALUE
}
