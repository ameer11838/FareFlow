package com.fareflow.recommendation.dto;

import java.util.List;

/**
 * One scored option in a recommendation response.
 *
 * @param labels     may contain more than one entry — the cheapest route is often
 *                   also the slowest, and a sole candidate carries all three
 * @param fareCents  integer cents, formatted for display by the client
 * @param overBudget true when this fare exceeds the user's remaining weekly budget.
 *                   Flagged, never hidden: the user still has to get where they are going.
 * @param vsFastest  null when this route is itself the fastest
 * @param vsBestValue null when this route is itself the best value
 * @param vsCheapest  null when this route is itself the cheapest
 * @param geometry    map shape; separate from fare and timing data by design
 */
public record RecommendedRouteDto(
        long routeId,
        String provider,
        String providerName,
        String mode,
        int durationMinutes,
        long fareCents,
        int transfers,
        List<String> labels,
        boolean recommended,
        double score,
        ScoreBreakdownDto breakdown,
        boolean overBudget,
        String explanation,
        RouteComparisonDto vsFastest,
        RouteComparisonDto vsBestValue,
        RouteComparisonDto vsCheapest,
        RouteGeometryDto geometry
) {
}
