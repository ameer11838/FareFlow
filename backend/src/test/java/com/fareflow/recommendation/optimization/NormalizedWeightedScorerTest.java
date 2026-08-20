package com.fareflow.recommendation.optimization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.fareflow.recommendation.optimization.RecommendationLabel.BEST_VALUE;
import static com.fareflow.recommendation.optimization.RecommendationLabel.CHEAPEST;
import static com.fareflow.recommendation.optimization.RecommendationLabel.FASTEST;
import static com.fareflow.recommendation.optimization.RouteFixtures.newarkToManhattan;
import static com.fareflow.recommendation.optimization.RouteFixtures.route;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The scoring engine is the technical core of FareFlow, so it gets the most
 * thorough tests. Everything here is plain JUnit: no Spring context, no database.
 */
class NormalizedWeightedScorerTest {

    private static final double TOLERANCE = 1e-4;

    private final NormalizedWeightedScorer scorer = new NormalizedWeightedScorer();

    @Nested
    @DisplayName("Newark -> Manhattan, the canonical example")
    class NewarkToManhattan {

        private final List<ScoredRoute> ranked =
                new NormalizedWeightedScorer().score(newarkToManhattan(), OptimizationWeights.defaults());

        @Test
        @DisplayName("PATH is Best Value")
        void pathIsBestValue() {
            assertThat(byId(ranked, 2L).hasLabel(BEST_VALUE)).isTrue();
            assertThat(ranked.getFirst().route().routeId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("NJ Transit is Fastest")
        void njTransitIsFastest() {
            assertThat(byId(ranked, 1L).hasLabel(FASTEST)).isTrue();
        }

        @Test
        @DisplayName("NYC Bus is Cheapest")
        void busIsCheapest() {
            assertThat(byId(ranked, 3L).hasLabel(CHEAPEST)).isTrue();
        }

        @Test
        @DisplayName("scores match the hand-computed values")
        void scoresMatchTheWorkedExample() {
            // normalizedFare: NJT (625-290)/335 = 1.0 | PATH 10/335 = 0.0299 | Bus 0.0
            // normalizedTime: NJT 0.0 | PATH 16/33 = 0.4848 | Bus 33/33 = 1.0
            assertThat(byId(ranked, 1L).score()).isCloseTo(0.4500, within());
            assertThat(byId(ranked, 2L).score()).isCloseTo(0.2316, within());
            assertThat(byId(ranked, 3L).score()).isCloseTo(0.4500, within());
        }

        @Test
        @DisplayName("breakdown exposes the normalized inputs")
        void breakdownIsExposed() {
            ScoreBreakdown path = byId(ranked, 2L).breakdown();
            assertThat(path.normalizedFare()).isCloseTo(0.0299, within());
            assertThat(path.normalizedTime()).isCloseTo(0.4848, within());
            assertThat(path.normalizedTransfers()).isEqualTo(0.0);
            assertThat(path.total()).isCloseTo(byId(ranked, 2L).score(), within());
        }

        @Test
        @DisplayName("NJ Transit and the bus tie exactly, and the tie-break is deterministic")
        void exactTieIsBrokenByFare() {
            // Both score 0.450. Tie-break rule 2 is lower fare, so the bus ranks ahead.
            assertThat(byId(ranked, 1L).score()).isCloseTo(byId(ranked, 3L).score(), within());
            assertThat(ranked.stream().map(scored -> scored.route().routeId()))
                    .containsExactly(2L, 3L, 1L);
        }
    }

    @Test
    @DisplayName("shifting weights toward time makes NJ Transit Best Value")
    void runningLateFlipsTheRecommendation() {
        // The "I'm running late" profile a future AI component would produce.
        List<ScoredRoute> ranked =
                scorer.score(newarkToManhattan(), OptimizationWeights.of(0.10, 0.85, 0.05));

        assertThat(ranked.getFirst().route().routeId()).isEqualTo(1L);
        assertThat(byId(ranked, 1L).hasLabel(BEST_VALUE)).isTrue();
        assertThat(byId(ranked, 1L).score()).isCloseTo(0.1000, within());
        assertThat(byId(ranked, 2L).score()).isCloseTo(0.4151, within());
        assertThat(byId(ranked, 3L).score()).isCloseTo(0.8500, within());
    }

    @Test
    @DisplayName("identical fares: the fare dimension contributes zero, no division by zero")
    void identicalFares() {
        List<ScoredRoute> ranked = scorer.score(List.of(
                route(1L, "Alpha", 20, 500, 0),
                route(2L, "Beta", 40, 500, 0),
                route(3L, "Gamma", 60, 500, 0)), OptimizationWeights.defaults());

        assertThat(ranked).allSatisfy(scored ->
                assertThat(scored.breakdown().normalizedFare()).isEqualTo(0.0));
        // Ranking is decided purely by time.
        assertThat(ranked.getFirst().route().routeId()).isEqualTo(1L);
        assertThat(ranked.getFirst().hasLabel(BEST_VALUE)).isTrue();
    }

    @Test
    @DisplayName("identical durations: the time dimension contributes zero")
    void identicalDurations() {
        List<ScoredRoute> ranked = scorer.score(List.of(
                route(1L, "Alpha", 30, 900, 0),
                route(2L, "Beta", 30, 400, 0),
                route(3L, "Gamma", 30, 650, 0)), OptimizationWeights.defaults());

        assertThat(ranked).allSatisfy(scored ->
                assertThat(scored.breakdown().normalizedTime()).isEqualTo(0.0));
        assertThat(ranked.getFirst().route().routeId()).isEqualTo(2L);
        assertThat(ranked.getFirst().labels()).contains(CHEAPEST, BEST_VALUE);
    }

    @Test
    @DisplayName("equal transfers: the transfer dimension contributes zero for every route")
    void equalTransfers() {
        // True of all the Phase 1 seed data, so this is not a hypothetical case.
        List<ScoredRoute> ranked = scorer.score(newarkToManhattan(), OptimizationWeights.defaults());

        assertThat(ranked).allSatisfy(scored -> {
            assertThat(scored.breakdown().normalizedTransfers()).isEqualTo(0.0);
            assertThat(scored.breakdown().transferContribution()).isEqualTo(0.0);
        });
    }

    @Test
    @DisplayName("all three attributes identical: every score is zero and order is by id")
    void everythingIdentical() {
        List<ScoredRoute> ranked = scorer.score(List.of(
                route(7L, "Alpha", 30, 500, 1),
                route(9L, "Beta", 30, 500, 1)), OptimizationWeights.defaults());

        assertThat(ranked).allSatisfy(scored -> assertThat(scored.score()).isEqualTo(0.0));
        // Fully tied, so the final tie-break (lowest id) decides.
        assertThat(ranked.stream().map(scored -> scored.route().routeId())).containsExactly(7L, 9L);
    }

    @Test
    @DisplayName("a single route carries all three labels")
    void singleRoute() {
        List<ScoredRoute> ranked =
                scorer.score(List.of(route(1L, "Alpha", 85, 1875, 1)), OptimizationWeights.defaults());

        assertThat(ranked).hasSize(1);
        assertThat(ranked.getFirst().labels()).containsExactlyInAnyOrder(CHEAPEST, FASTEST, BEST_VALUE);
        assertThat(ranked.getFirst().score()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("no routes returns an empty list rather than throwing")
    void noRoutes() {
        assertThat(scorer.score(List.of(), OptimizationWeights.defaults())).isEmpty();
    }

    @Test
    @DisplayName("near-identical scores are treated as tied rather than compared with ==")
    void nearTieUsesEpsilon() {
        // Two routes whose scores differ by far less than SCORE_EPSILON must be
        // ordered by the tie-break chain, not by floating point noise.
        List<ScoredRoute> ranked = scorer.score(List.of(
                route(1L, "Pricey", 10, 1000, 0),
                route(2L, "Cheap", 20, 500, 0)), OptimizationWeights.of(0.5, 0.5, 0.0));

        // Both normalize to 0.5 exactly: one is worst on fare, the other worst on time.
        assertThat(ranked.getFirst().score())
                .isCloseTo(ranked.get(1).score(), within());
        // Tie-break rule 2 (lower fare) puts the cheaper route first.
        assertThat(ranked.getFirst().route().routeId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("a route that is both cheapest and fastest wins outright")
    void dominantRouteTakesEveryLabel() {
        List<ScoredRoute> ranked = scorer.score(List.of(
                route(1L, "Best", 10, 200, 0),
                route(2L, "Worse", 40, 800, 1)), OptimizationWeights.defaults());

        assertThat(ranked.getFirst().route().routeId()).isEqualTo(1L);
        assertThat(ranked.getFirst().labels()).containsExactlyInAnyOrder(CHEAPEST, FASTEST, BEST_VALUE);
        assertThat(ranked.get(1).labels()).isEmpty();
    }

    @Test
    @DisplayName("transfers break an otherwise perfect tie")
    void transfersInfluenceScoring() {
        List<ScoredRoute> ranked = scorer.score(List.of(
                route(1L, "Direct", 30, 500, 0),
                route(2L, "Connecting", 30, 500, 2)), OptimizationWeights.defaults());

        assertThat(ranked.getFirst().route().routeId()).isEqualTo(1L);
        assertThat(ranked.get(1).breakdown().normalizedTransfers()).isEqualTo(1.0);
        assertThat(ranked.get(1).score()).isCloseTo(0.10, within());
    }

    @Test
    @DisplayName("null arguments are rejected")
    void nullArgumentsRejected() {
        assertThatThrownBy(() -> scorer.score(null, OptimizationWeights.defaults()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> scorer.score(newarkToManhattan(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("scoring is deterministic across repeated runs")
    void deterministic() {
        List<ScoredRoute> first = scorer.score(newarkToManhattan(), OptimizationWeights.defaults());
        List<ScoredRoute> second = scorer.score(newarkToManhattan(), OptimizationWeights.defaults());

        assertThat(first.stream().map(scored -> scored.route().routeId()).toList())
                .isEqualTo(second.stream().map(scored -> scored.route().routeId()).toList());
        assertThat(first.getFirst().score()).isEqualTo(second.getFirst().score());
    }

    private static ScoredRoute byId(List<ScoredRoute> routes, long id) {
        return routes.stream()
                .filter(scored -> scored.route().routeId() == id)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No scored route with id " + id));
    }

    private static org.assertj.core.data.Offset<Double> within() {
        return org.assertj.core.data.Offset.offset(TOLERANCE);
    }
}
