package com.fareflow.recommendation.dto;

import java.util.List;

/**
 * Result of a route search.
 *
 * <p>An empty {@code options} list with HTTP 200 is the correct response when no
 * routes exist for the pair — a search that finds nothing is still a successful
 * search. 404 would mean the endpoint itself does not exist.
 *
 * @param contextNote non-null only when the selected profile changed which route
 *                    won relative to BALANCED. Silence is better than telling the
 *                    user their choice made no difference.
 */
public record RecommendationResponse(
        String origin,
        String destination,
        ProfileDto profile,
        WeightsDto weightsUsed,
        String summary,
        String contextNote,
        List<RecommendedRouteDto> options
) {

    public static RecommendationResponse empty(String origin, String destination,
                                               ProfileDto profile, WeightsDto weights, String summary) {
        return new RecommendationResponse(origin, destination, profile, weights, summary, null, List.of());
    }
}
