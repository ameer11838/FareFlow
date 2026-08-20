package com.fareflow.recommendation.optimization;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * A candidate route together with its score, the arithmetic behind that score,
 * and any labels it earned. Lower scores are better.
 */
public record ScoredRoute(
        RouteCandidate route,
        double score,
        ScoreBreakdown breakdown,
        Set<RecommendationLabel> labels
) {

    public ScoredRoute {
        if (route == null || breakdown == null || labels == null) {
            throw new IllegalArgumentException("route, breakdown and labels are required");
        }
        labels = labels.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(labels));
    }

    public boolean hasLabel(RecommendationLabel label) {
        return labels.contains(label);
    }

    /** Returns a copy of this route carrying the supplied labels. */
    public ScoredRoute withLabels(Set<RecommendationLabel> newLabels) {
        return new ScoredRoute(route, score, breakdown, newLabels);
    }
}
