package com.fareflow.recommendation.optimization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.fareflow.recommendation.optimization.RouteFixtures.newarkToManhattan;
import static com.fareflow.recommendation.optimization.RouteFixtures.route;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Explanations must be derived from the same integers that drove the scoring, so
 * they can never contradict the recommendation. No randomness, no language model.
 */
class ExplanationBuilderTest {

    private final NormalizedWeightedScorer scorer = new NormalizedWeightedScorer();
    private final ExplanationBuilder builder = new ExplanationBuilder();

    @Test
    @DisplayName("Best Value is explained against the fastest route with correct deltas")
    void bestValueExplainedAgainstFastest() {
        Map<Long, String> explanations = explain(newarkToManhattan(), OptimizationWeights.defaults());

        // PATH ($3.00, 38min) vs NJ Transit ($6.25, 22min): $3.25 cheaper, 16 minutes slower.
        assertThat(explanations.get(2L))
                .contains("PATH")
                .contains("$3.25")
                .contains("NJ Transit")
                .contains("16 minutes")
                .contains("$0.20 per minute");
    }

    @Test
    @DisplayName("the fastest route is explained as costing more but saving time")
    void fastestExplainedAgainstBestValue() {
        Map<Long, String> explanations = explain(newarkToManhattan(), OptimizationWeights.defaults());

        assertThat(explanations.get(1L))
                .startsWith("Fastest option.")
                .contains("NJ Transit")
                .contains("$3.25 more")
                .contains("16 minutes");
    }

    @Test
    @DisplayName("the cheapest route is explained against Best Value")
    void cheapestExplained() {
        Map<Long, String> explanations = explain(newarkToManhattan(), OptimizationWeights.defaults());

        // NYC Bus ($2.90, 55min) vs PATH ($3.00, 38min): $0.10 cheaper, 17 minutes slower.
        assertThat(explanations.get(3L))
                .startsWith("Cheapest option.")
                .contains("$0.10")
                .contains("17 minutes");
    }

    @Test
    @DisplayName("a lone route says so instead of inventing a comparison")
    void singleRoute() {
        Map<Long, String> explanations =
                explain(List.of(route(1L, "NJ Transit", 85, 1875, 1)), OptimizationWeights.defaults());

        assertThat(explanations.get(1L)).contains("Only one route is available");
    }

    @Test
    @DisplayName("no routes yields no explanations")
    void noRoutes() {
        assertThat(builder.explain(List.of())).isEmpty();
        assertThat(builder.summarize(List.of())).contains("No routes are available");
    }

    @Test
    @DisplayName("a route holding every label is described as the clear choice")
    void dominantRoute() {
        List<ScoredRoute> ranked = scorer.score(List.of(
                route(1L, "Express", 10, 200, 0),
                route(2L, "Slow", 40, 800, 0)), OptimizationWeights.defaults());

        assertThat(builder.explain(ranked).get(1L))
                .isEqualTo("Express is the fastest, cheapest, and best-value option available.");
        assertThat(builder.summarize(ranked)).contains("fastest and the cheapest");
    }

    @Test
    @DisplayName("equal fares are described without a bogus per-minute rate")
    void sameFareDifferentTime() {
        List<ScoredRoute> ranked = scorer.score(List.of(
                route(1L, "Quick", 20, 500, 0),
                route(2L, "Slow", 45, 500, 0)), OptimizationWeights.defaults());

        assertThat(builder.explain(ranked).get(2L))
                .contains("at the same fare")
                .doesNotContain("per minute");
    }

    @Test
    @DisplayName("equal durations are described without dividing by a zero time delta")
    void sameTimeDifferentFare() {
        // The riskiest division by zero in the system lives in the explanation,
        // not the scoring: a per-minute rate over a zero-minute delta.
        List<ScoredRoute> ranked = scorer.score(List.of(
                route(1L, "Cheap", 30, 400, 0),
                route(2L, "Pricey", 30, 900, 0)), OptimizationWeights.defaults());

        assertThat(builder.explain(ranked).get(2L))
                .contains("for the same travel time")
                .doesNotContain("per minute");
    }

    @Test
    @DisplayName("identical routes produce a sensible sentence, not a crash")
    void identicalRoutes() {
        List<ScoredRoute> ranked = scorer.score(List.of(
                route(1L, "Alpha", 30, 500, 0),
                route(2L, "Beta", 30, 500, 0)), OptimizationWeights.defaults());

        assertThat(builder.explain(ranked).get(2L))
                .contains("costs the same and takes the same time");
    }

    @Test
    @DisplayName("summary names the best-value provider")
    void summaryNamesTheWinner() {
        List<ScoredRoute> ranked = scorer.score(newarkToManhattan(), OptimizationWeights.defaults());
        assertThat(builder.summarize(ranked)).isEqualTo("PATH balances cost and travel time best for this trip.");
    }

    @Test
    @DisplayName("explanations are stable across repeated calls")
    void deterministic() {
        List<ScoredRoute> ranked = scorer.score(newarkToManhattan(), OptimizationWeights.defaults());
        assertThat(builder.explain(ranked)).isEqualTo(builder.explain(ranked));
        assertThat(builder.summarize(ranked)).isEqualTo(builder.summarize(ranked));
    }

    private Map<Long, String> explain(List<RouteCandidate> candidates, OptimizationWeights weights) {
        return builder.explain(scorer.score(candidates, weights));
    }
}
