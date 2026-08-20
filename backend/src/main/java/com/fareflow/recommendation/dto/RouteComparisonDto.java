package com.fareflow.recommendation.dto;

/**
 * A route's difference from a reference route, in the same integer units the engine
 * used. The UI renders these as short trade-off lines ("Saves $3.25", "16 min slower")
 * rather than parsing sentences out of the prose explanation.
 *
 * @param fareDeltaCents positive when this route costs more than the reference
 * @param minutesDelta   positive when this route is slower than the reference
 */
public record RouteComparisonDto(
        long referenceRouteId,
        String referenceProvider,
        long fareDeltaCents,
        int minutesDelta,
        int transfersDelta
) {
}
