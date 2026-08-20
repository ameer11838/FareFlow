package com.fareflow.recommendation.optimization;

/**
 * Decides which {@link OptimizationWeights} apply to a given request.
 *
 * <p><strong>This interface is where AI plugs in later.</strong> Phase 1 ships
 * {@link DefaultPreferenceResolver}. A future {@code AiPreferenceResolver} would
 * translate natural language ("I'm running late for an interview") into a weight
 * proposal, run it through a sanitiser, and return it here. Nothing downstream —
 * the scorer, the trip service, the ledger — would change, and none of them are
 * reachable from that resolver.
 */
public interface PreferenceResolver {

    OptimizationWeights resolve(PreferenceContext context);
}
