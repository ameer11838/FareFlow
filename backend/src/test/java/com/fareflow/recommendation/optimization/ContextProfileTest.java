package com.fareflow.recommendation.optimization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static com.fareflow.recommendation.optimization.RouteFixtures.newarkToManhattan;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Context profiles are the deterministic stand-in for the natural-language layer.
 * These tests pin the behaviour a future AI component must reproduce.
 */
class ContextProfileTest {

    private final NormalizedWeightedScorer scorer = new NormalizedWeightedScorer();
    private final DefaultPreferenceResolver resolver = DefaultPreferenceResolver.withDefaults();

    private String winnerUnder(ContextProfile profile) {
        OptimizationWeights weights = resolver.resolve(PreferenceContext.anonymous(profile));
        List<ScoredRoute> ranked = scorer.score(newarkToManhattan(), weights);
        return ranked.getFirst().route().providerDisplayName();
    }

    @Test
    @DisplayName("BALANCED recommends PATH")
    void balancedPicksPath() {
        assertThat(winnerUnder(ContextProfile.BALANCED)).isEqualTo("PATH");
    }

    @Test
    @DisplayName("RUSH recommends NJ Transit")
    void rushPicksNjTransit() {
        assertThat(winnerUnder(ContextProfile.RUSH)).isEqualTo("NJ Transit");
    }

    @Test
    @DisplayName("SAVE_MONEY stays with PATH here, because the bus saves only $0.10 for 17 more minutes")
    void saveMoneyIsValueAwareNotBlindlyCheapest() {
        // Worth pinning: "save me money" does NOT mean "always pick the lowest fare".
        // On this data the cheapest route saves $0.10 and costs 17 extra minutes, so
        // even at a 0.75 cost weight PATH still scores best. Recommending the bus here
        // would be bad advice, and the engine correctly declines to give it.
        assertThat(winnerUnder(ContextProfile.SAVE_MONEY)).isEqualTo("PATH");

        OptimizationWeights weights =
                resolver.resolve(PreferenceContext.anonymous(ContextProfile.SAVE_MONEY));
        assertThat(weights.costPriority()).isEqualTo(0.75);
    }

    @Test
    @DisplayName("SAVE_MONEY switches to the cheapest route once the fare gap is real")
    void saveMoneyPicksTheCheapestWhenItMatters() {
        // Express $10.00/20min, Mid $6.00/30min, Local $3.00/45min.
        List<RouteCandidate> candidates = List.of(
                new RouteCandidate(1L, "EXPRESS", "Express", "RAIL", 20, 1000, 0),
                new RouteCandidate(2L, "MID", "Mid", "RAIL", 30, 600, 0),
                new RouteCandidate(3L, "LOCAL", "Local", "BUS", 45, 300, 0));

        String balanced = scorer.score(candidates,
                resolver.resolve(PreferenceContext.anonymous(ContextProfile.BALANCED)))
                .getFirst().route().providerDisplayName();
        String saving = scorer.score(candidates,
                resolver.resolve(PreferenceContext.anonymous(ContextProfile.SAVE_MONEY)))
                .getFirst().route().providerDisplayName();

        assertThat(balanced).isEqualTo("Mid");
        assertThat(saving).isEqualTo("Local");
    }

    @Test
    @DisplayName("FEWER_TRANSFERS switches to the direct route")
    void fewerTransfersAvoidsConnections() {
        // Three candidates, not two: with only two routes every attribute normalizes
        // to exactly 0 or 1, which makes the comparison degenerate.
        List<RouteCandidate> candidates = List.of(
                new RouteCandidate(1L, "CONNECTING", "Connecting", "BUS", 30, 300, 2),
                new RouteCandidate(2L, "DIRECT", "Direct", "RAIL", 40, 400, 0),
                new RouteCandidate(3L, "SCENIC", "Scenic", "FERRY", 55, 480, 1));

        OptimizationWeights balanced =
                resolver.resolve(PreferenceContext.anonymous(ContextProfile.BALANCED));
        OptimizationWeights transfers =
                resolver.resolve(PreferenceContext.anonymous(ContextProfile.FEWER_TRANSFERS));

        // Cheapest and fastest wins on balance despite its two transfers...
        assertThat(scorer.score(candidates, balanced).getFirst().route().routeId()).isEqualTo(1L);
        // ...but weighting transfers at 0.50 flips the recommendation to the direct route.
        assertThat(scorer.score(candidates, transfers).getFirst().route().routeId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("profile weights match the documented values")
    void weightsAreAsDocumented() {
        assertThat(ContextProfile.RUSH.timePriority()).isEqualTo(0.75);
        assertThat(ContextProfile.RUSH.costPriority()).isEqualTo(0.15);
        assertThat(ContextProfile.SAVE_MONEY.costPriority()).isEqualTo(0.75);
        assertThat(ContextProfile.FEWER_TRANSFERS.transferPriority()).isEqualTo(0.50);
    }

    @ParameterizedTest
    @EnumSource(ContextProfile.class)
    @DisplayName("every profile produces valid weights that sum to 1")
    void allProfilesAreValid(ContextProfile profile) {
        OptimizationWeights weights = resolver.resolve(PreferenceContext.anonymous(profile));
        assertThat(weights.costPriority() + weights.timePriority() + weights.transferPriority())
                .isEqualTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("profile names parse case-insensitively and unknown values are rejected")
    void parsing() {
        assertThat(ContextProfile.parse("rush")).contains(ContextProfile.RUSH);
        assertThat(ContextProfile.parse("SAVE_MONEY")).contains(ContextProfile.SAVE_MONEY);
        assertThat(ContextProfile.parse("save-money")).contains(ContextProfile.SAVE_MONEY);
        // Absent input means "no stated preference", not an error.
        assertThat(ContextProfile.parse(null)).contains(ContextProfile.BALANCED);
        assertThat(ContextProfile.parse("  ")).contains(ContextProfile.BALANCED);
        assertThat(ContextProfile.parse("YOLO")).isEmpty();
    }

    @Test
    @DisplayName("a profile still respects budget pressure on top of its own stance")
    void budgetPressureLayersOnTopOfTheProfile() {
        // RUSH is 0.15/0.75/0.10. At full pressure: shift = 0.4 * 1.0 * 0.75 = 0.30
        OptimizationWeights weights = resolver.resolve(
                new PreferenceContext(1L, 1_000L, 1_000L, ContextProfile.RUSH));

        assertThat(weights.costPriority()).isEqualTo(0.45, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(weights.timePriority()).isEqualTo(0.45, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(weights.source()).isEqualTo(WeightSource.BUDGET_ADJUSTED);
    }

    @Test
    @DisplayName("a context note explains a profile-driven switch")
    void contextNoteExplainsTheSwitch() {
        ExplanationBuilder builder = new ExplanationBuilder();

        ScoredRoute rushWinner = scorer.score(newarkToManhattan(),
                resolver.resolve(PreferenceContext.anonymous(ContextProfile.RUSH))).getFirst();
        ScoredRoute balancedWinner = scorer.score(newarkToManhattan(),
                resolver.resolve(PreferenceContext.anonymous(ContextProfile.BALANCED))).getFirst();

        String note = builder.contextNote(ContextProfile.RUSH, rushWinner, balancedWinner);

        assertThat(note)
                .contains("I'm in a rush")
                .contains("NJ Transit")
                .contains("$3.25")
                .contains("16 minutes sooner");
    }

    @Test
    @DisplayName("no note when the profile did not change the outcome")
    void noNoteWhenNothingChanged() {
        ExplanationBuilder builder = new ExplanationBuilder();
        ScoredRoute winner = scorer.score(newarkToManhattan(),
                resolver.resolve(PreferenceContext.anonymous(ContextProfile.BALANCED))).getFirst();

        assertThat(builder.contextNote(ContextProfile.BALANCED, winner, winner)).isNull();
        assertThat(builder.contextNote(ContextProfile.RUSH, winner, winner)).isNull();
    }

    @Test
    @DisplayName("the profile enum exposes no monetary fields")
    void profilesCarryNoMoney() {
        // Same guard as OptimizationWeights: profiles describe priorities, not prices.
        assertThat(ContextProfile.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .filteredOn(name -> !name.equals("$VALUES") && !name.startsWith("ENUM$"))
                .allSatisfy(name -> assertThat(name.toLowerCase())
                        .doesNotContain("fare").doesNotContain("cents").doesNotContain("price"));
    }
}
