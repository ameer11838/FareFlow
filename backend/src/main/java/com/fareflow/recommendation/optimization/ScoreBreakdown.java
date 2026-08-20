package com.fareflow.recommendation.optimization;

/**
 * The intermediate values behind a route's score.
 *
 * <p>Returned on every recommendation so a decision can be verified by hand and
 * replayed later. When AI eventually influences the weights, this breakdown is
 * how you prove it only moved the priorities and never picked the route.
 *
 * <p>Each normalised value is in [0,1] where 0 is best in the candidate set and 1
 * is worst. Each contribution is that value multiplied by its weight.
 */
public record ScoreBreakdown(
        double normalizedFare,
        double normalizedTime,
        double normalizedTransfers,
        double fareContribution,
        double timeContribution,
        double transferContribution
) {

    /** Sum of the three weighted contributions — equal to the route's score. */
    public double total() {
        return fareContribution + timeContribution + transferContribution;
    }
}
