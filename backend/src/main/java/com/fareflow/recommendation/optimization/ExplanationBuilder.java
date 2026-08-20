package com.fareflow.recommendation.optimization;

import com.fareflow.common.Money;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns a scored route set into human-readable explanations.
 *
 * <p>Every sentence is derived from the same integer-cent and integer-minute
 * values that drove the scoring, so an explanation can never disagree with the
 * recommendation it describes. No language model is involved and there is no
 * randomness: the same input always produces the same words.
 *
 * <p>Plain Java — no Spring, no database.
 */
public final class ExplanationBuilder {

    /**
     * Builds one explanation per route, keyed by route id.
     *
     * @param ranked routes ordered best-value-first, as returned by {@link RouteScorer}
     */
    public Map<Long, String> explain(List<ScoredRoute> ranked) {
        if (ranked == null || ranked.isEmpty()) {
            return Map.of();
        }

        if (ranked.size() == 1) {
            ScoredRoute only = ranked.getFirst();
            return Map.of(only.route().routeId(),
                    "Only one route is available for this trip, so there is nothing to compare it against.");
        }

        ScoredRoute bestValue = find(ranked, RecommendationLabel.BEST_VALUE).orElse(ranked.getFirst());
        ScoredRoute cheapest = find(ranked, RecommendationLabel.CHEAPEST).orElse(bestValue);
        ScoredRoute fastest = find(ranked, RecommendationLabel.FASTEST).orElse(bestValue);

        Map<Long, String> explanations = new HashMap<>();
        for (ScoredRoute route : ranked) {
            explanations.put(route.route().routeId(), explainOne(route, bestValue, cheapest, fastest));
        }
        return Map.copyOf(explanations);
    }

    /**
     * A one-line summary of the recommendation as a whole.
     */
    public String summarize(List<ScoredRoute> ranked) {
        if (ranked == null || ranked.isEmpty()) {
            return "No routes are available for this origin and destination.";
        }
        if (ranked.size() == 1) {
            return "%s is the only route available for this trip."
                    .formatted(ranked.getFirst().route().providerDisplayName());
        }

        ScoredRoute bestValue = find(ranked, RecommendationLabel.BEST_VALUE).orElse(ranked.getFirst());
        if (bestValue.labels().size() == 3) {
            return "%s is the fastest and the cheapest option, so it is the clear choice."
                    .formatted(bestValue.route().providerDisplayName());
        }
        if (bestValue.hasLabel(RecommendationLabel.FASTEST)) {
            return "%s is the fastest option and still scores best on value."
                    .formatted(bestValue.route().providerDisplayName());
        }
        if (bestValue.hasLabel(RecommendationLabel.CHEAPEST)) {
            return "%s is the cheapest option and still scores best on value."
                    .formatted(bestValue.route().providerDisplayName());
        }
        return "%s balances cost and travel time best for this trip."
                .formatted(bestValue.route().providerDisplayName());
    }

    private String explainOne(ScoredRoute route,
                              ScoredRoute bestValue,
                              ScoredRoute cheapest,
                              ScoredRoute fastest) {

        if (route.labels().size() == 3) {
            return "%s is the fastest, cheapest, and best-value option available."
                    .formatted(route.route().providerDisplayName());
        }

        if (route.hasLabel(RecommendationLabel.BEST_VALUE)) {
            // Explain the winner against whichever labelled alternatives exist.
            if (!isSame(route, fastest)) {
                return compare(route, fastest);
            }
            if (!isSame(route, cheapest)) {
                return compare(route, cheapest);
            }
            return "%s offers the best balance of cost and travel time here."
                    .formatted(route.route().providerDisplayName());
        }

        // Everything else is explained relative to the best-value route.
        String comparison = compare(route, bestValue);
        if (route.hasLabel(RecommendationLabel.FASTEST)) {
            return "Fastest option. " + comparison;
        }
        if (route.hasLabel(RecommendationLabel.CHEAPEST)) {
            return "Cheapest option. " + comparison;
        }
        return comparison;
    }

    /**
     * Describes {@code subject} relative to {@code reference} in terms of fare and time.
     */
    private String compare(ScoredRoute subject, ScoredRoute reference) {
        String subjectName = subject.route().providerDisplayName();
        String referenceName = reference.route().providerDisplayName();

        long fareDelta = subject.route().fareCents() - reference.route().fareCents();
        int timeDelta = subject.route().durationMinutes() - reference.route().durationMinutes();

        if (fareDelta == 0 && timeDelta == 0) {
            return "%s costs the same and takes the same time as %s."
                    .formatted(subjectName, referenceName);
        }
        if (fareDelta == 0) {
            return timeDelta < 0
                    ? "%s arrives %s sooner than %s at the same fare."
                        .formatted(subjectName, Money.formatMinutes(-timeDelta), referenceName)
                    : "%s takes %s longer than %s at the same fare."
                        .formatted(subjectName, Money.formatMinutes(timeDelta), referenceName);
        }
        if (timeDelta == 0) {
            return fareDelta < 0
                    ? "%s saves you %s versus %s for the same travel time."
                        .formatted(subjectName, Money.format(-fareDelta), referenceName)
                    : "%s costs %s more than %s for the same travel time."
                        .formatted(subjectName, Money.format(fareDelta), referenceName);
        }

        // Strictly better or strictly worse on both dimensions.
        if (fareDelta < 0 && timeDelta < 0) {
            return "%s is both %s cheaper and %s faster than %s."
                    .formatted(subjectName, Money.format(-fareDelta),
                            Money.formatMinutes(-timeDelta), referenceName);
        }
        if (fareDelta > 0 && timeDelta > 0) {
            return "%s costs %s more than %s and takes %s longer."
                    .formatted(subjectName, Money.format(fareDelta), referenceName,
                            Money.formatMinutes(timeDelta));
        }

        // The interesting case: a genuine trade-off between money and time.
        if (fareDelta < 0) {
            // Cheaper but slower.
            String rate = Money.formatPerMinute(-fareDelta, timeDelta);
            return "%s saves you %s versus %s while adding %s — about %s per minute of time given up."
                    .formatted(subjectName, Money.format(-fareDelta), referenceName,
                            Money.formatMinutes(timeDelta), rate);
        }
        // Pricier but faster.
        String rate = Money.formatPerMinute(fareDelta, -timeDelta);
        return "%s costs %s more than %s but saves %s — about %s per minute saved."
                .formatted(subjectName, Money.format(fareDelta), referenceName,
                        Money.formatMinutes(-timeDelta), rate);
    }


    /**
     * A note explaining why a chosen {@link ContextProfile} changed the recommendation.
     *
     * <p>Produced by scoring the same candidates twice — once under the profile and
     * once under BALANCED — and describing the difference. Returns null when the
     * profile did not change the outcome, so the UI shows nothing rather than a
     * meaningless "we did what we would have done anyway".
     *
     * @param profile        the stance the user selected
     * @param chosen         the winner under that stance
     * @param balancedChoice the winner under BALANCED
     */
    public String contextNote(ContextProfile profile, ScoredRoute chosen, ScoredRoute balancedChoice) {
        if (profile == null || profile == ContextProfile.BALANCED || chosen == null || balancedChoice == null) {
            return null;
        }
        if (isSame(chosen, balancedChoice)) {
            return null;
        }

        String chosenName = chosen.route().providerDisplayName();
        String balancedName = balancedChoice.route().providerDisplayName();

        long fareDelta = chosen.route().fareCents() - balancedChoice.route().fareCents();
        int timeDelta = chosen.route().durationMinutes() - balancedChoice.route().durationMinutes();

        String lead = "You told FareFlow \"%s\", so it is %s."
                .formatted(profile.displayName(), profile.rationale());

        String tradeoff;
        if (fareDelta > 0 && timeDelta < 0) {
            tradeoff = "%s costs %s more than %s but gets you there %s sooner."
                    .formatted(chosenName, Money.format(fareDelta), balancedName,
                            Money.formatMinutes(-timeDelta));
        } else if (fareDelta < 0 && timeDelta > 0) {
            tradeoff = "%s saves you %s versus %s, arriving %s later."
                    .formatted(chosenName, Money.format(-fareDelta), balancedName,
                            Money.formatMinutes(timeDelta));
        } else if (fareDelta == 0 && timeDelta != 0) {
            tradeoff = timeDelta < 0
                    ? "%s arrives %s sooner than %s at the same fare."
                        .formatted(chosenName, Money.formatMinutes(-timeDelta), balancedName)
                    : "%s takes %s longer than %s at the same fare."
                        .formatted(chosenName, Money.formatMinutes(timeDelta), balancedName);
        } else if (timeDelta == 0 && fareDelta != 0) {
            tradeoff = fareDelta < 0
                    ? "%s saves you %s versus %s for the same travel time."
                        .formatted(chosenName, Money.format(-fareDelta), balancedName)
                    : "%s costs %s more than %s for the same travel time."
                        .formatted(chosenName, Money.format(fareDelta), balancedName);
        } else if (chosen.route().transfers() < balancedChoice.route().transfers()) {
            tradeoff = "%s has fewer transfers than %s.".formatted(chosenName, balancedName);
        } else {
            tradeoff = "%s scores better than %s under this stance."
                    .formatted(chosenName, balancedName);
        }

        return lead + " " + tradeoff;
    }

    private static boolean isSame(ScoredRoute left, ScoredRoute right) {
        return left.route().routeId() == right.route().routeId();
    }

    private static Optional<ScoredRoute> find(List<ScoredRoute> routes, RecommendationLabel label) {
        return routes.stream().filter(route -> route.hasLabel(label)).findFirst();
    }
}
