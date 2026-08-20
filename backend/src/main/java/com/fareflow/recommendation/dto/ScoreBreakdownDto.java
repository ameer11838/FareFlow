package com.fareflow.recommendation.dto;

import com.fareflow.recommendation.optimization.ScoreBreakdown;

/** The arithmetic behind a route's score, exposed so decisions can be audited. */
public record ScoreBreakdownDto(
        double normalizedFare,
        double normalizedTime,
        double normalizedTransfers,
        double fareContribution,
        double timeContribution,
        double transferContribution
) {

    public static ScoreBreakdownDto from(ScoreBreakdown breakdown) {
        return new ScoreBreakdownDto(
                breakdown.normalizedFare(),
                breakdown.normalizedTime(),
                breakdown.normalizedTransfers(),
                breakdown.fareContribution(),
                breakdown.timeContribution(),
                breakdown.transferContribution());
    }
}
