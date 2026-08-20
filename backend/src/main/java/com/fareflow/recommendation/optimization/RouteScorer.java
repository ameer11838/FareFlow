package com.fareflow.recommendation.optimization;

import java.util.List;

/**
 * Scores and labels a set of candidate routes.
 *
 * <p>An interface rather than a concrete class so the scoring strategy can be
 * replaced later — for example with a generalised-cost model once fare caps and
 * weekly passes make absolute dollar comparisons meaningful — without touching
 * the service or controller layers.
 */
public interface RouteScorer {

    /**
     * Scores every candidate and returns them ordered best-first under the
     * deterministic tie-breaking rules.
     *
     * @return an ordered list, or an empty list when {@code candidates} is empty
     */
    List<ScoredRoute> score(List<RouteCandidate> candidates, OptimizationWeights weights);
}
