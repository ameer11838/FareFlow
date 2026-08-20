package com.fareflow.recommendation.optimization;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Scores routes with a min-max normalised weighted sum. Lower score wins.
 *
 * <p><strong>The problem this solves:</strong> you cannot add dollars to minutes.
 * So instead of comparing raw values, each attribute is rescaled to [0,1] across
 * the candidate set, where 0 is the best value present and 1 is the worst. Those
 * unitless numbers can then be weighted and summed.
 *
 * <pre>
 *   normalizedFare = (fare - minFare) / (maxFare - minFare)
 *   score = costPriority*normalizedFare + timePriority*normalizedTime
 *                                       + transferPriority*normalizedTransfers
 * </pre>
 *
 * <p><strong>Division by zero:</strong> when {@code max == min} for an attribute,
 * the normalised value is 0 for every route. That is the correct answer rather
 * than a defensive hack — an attribute on which all routes are identical cannot
 * distinguish them, so it contributes the same constant (zero) to every score and
 * therefore cannot affect the ranking. Note that the remaining weights are
 * <em>not</em> renormalised: absolute scores come out lower, relative order does not change.
 *
 * <p>This class is plain Java. It has no Spring, JPA, or HTTP dependency and can
 * be constructed directly in a unit test.
 */
public final class NormalizedWeightedScorer implements RouteScorer {

    /**
     * Scores closer together than this are treated as equal. Never compare
     * doubles with {@code ==} — 0.1 + 0.2 does not equal 0.3 in binary floating point.
     */
    public static final double SCORE_EPSILON = 1e-9;

    /** Best value: lowest score, then cheaper, then faster, then fewer transfers, then lowest id. */
    public static final Comparator<ScoredRoute> BEST_VALUE_ORDER =
            (left, right) -> {
                if (Math.abs(left.score() - right.score()) > SCORE_EPSILON) {
                    return Double.compare(left.score(), right.score());
                }
                return compareByFareThenTimeThenTransfersThenId(left, right);
            };

    /** Cheapest: lowest fare, then faster, then fewer transfers, then lowest id. */
    public static final Comparator<ScoredRoute> CHEAPEST_ORDER =
            Comparator.comparingLong((ScoredRoute scored) -> scored.route().fareCents())
                    .thenComparingInt(scored -> scored.route().durationMinutes())
                    .thenComparingInt(scored -> scored.route().transfers())
                    .thenComparingLong(scored -> scored.route().routeId());

    /** Fastest: shortest duration, then cheaper, then fewer transfers, then lowest id. */
    public static final Comparator<ScoredRoute> FASTEST_ORDER =
            Comparator.comparingInt((ScoredRoute scored) -> scored.route().durationMinutes())
                    .thenComparingLong(scored -> scored.route().fareCents())
                    .thenComparingInt(scored -> scored.route().transfers())
                    .thenComparingLong(scored -> scored.route().routeId());

    @Override
    public List<ScoredRoute> score(List<RouteCandidate> candidates, OptimizationWeights weights) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        Objects.requireNonNull(weights, "weights must not be null");

        if (candidates.isEmpty()) {
            return List.of();
        }

        Range fareRange = Range.ofLongs(candidates, RouteCandidate::fareCents);
        Range timeRange = Range.ofInts(candidates, RouteCandidate::durationMinutes);
        Range transferRange = Range.ofInts(candidates, RouteCandidate::transfers);

        List<ScoredRoute> scored = new ArrayList<>(candidates.size());
        for (RouteCandidate candidate : candidates) {
            double normalizedFare = fareRange.normalize(candidate.fareCents());
            double normalizedTime = timeRange.normalize(candidate.durationMinutes());
            double normalizedTransfers = transferRange.normalize(candidate.transfers());

            double fareContribution = weights.costPriority() * normalizedFare;
            double timeContribution = weights.timePriority() * normalizedTime;
            double transferContribution = weights.transferPriority() * normalizedTransfers;

            ScoreBreakdown breakdown = new ScoreBreakdown(
                    normalizedFare, normalizedTime, normalizedTransfers,
                    fareContribution, timeContribution, transferContribution);

            scored.add(new ScoredRoute(
                    candidate,
                    fareContribution + timeContribution + transferContribution,
                    breakdown,
                    Set.<RecommendationLabel>of()));
        }

        return applyLabels(scored);
    }

    /**
     * Assigns labels and returns the list ordered best-value-first. A route can
     * earn more than one label; with a single candidate it earns all three.
     */
    private static List<ScoredRoute> applyLabels(List<ScoredRoute> scored) {
        ScoredRoute cheapest = scored.stream().min(CHEAPEST_ORDER).orElseThrow();
        ScoredRoute fastest = scored.stream().min(FASTEST_ORDER).orElseThrow();
        ScoredRoute bestValue = scored.stream().min(BEST_VALUE_ORDER).orElseThrow();

        List<ScoredRoute> labeled = new ArrayList<>(scored.size());
        for (ScoredRoute route : scored) {
            EnumSet<RecommendationLabel> labels = EnumSet.noneOf(RecommendationLabel.class);
            long id = route.route().routeId();
            if (id == cheapest.route().routeId()) {
                labels.add(RecommendationLabel.CHEAPEST);
            }
            if (id == fastest.route().routeId()) {
                labels.add(RecommendationLabel.FASTEST);
            }
            if (id == bestValue.route().routeId()) {
                labels.add(RecommendationLabel.BEST_VALUE);
            }
            labeled.add(route.withLabels(labels));
        }

        labeled.sort(BEST_VALUE_ORDER);
        return List.copyOf(labeled);
    }

    private static int compareByFareThenTimeThenTransfersThenId(ScoredRoute left, ScoredRoute right) {
        int byFare = Long.compare(left.route().fareCents(), right.route().fareCents());
        if (byFare != 0) {
            return byFare;
        }
        int byDuration = Integer.compare(left.route().durationMinutes(), right.route().durationMinutes());
        if (byDuration != 0) {
            return byDuration;
        }
        int byTransfers = Integer.compare(left.route().transfers(), right.route().transfers());
        if (byTransfers != 0) {
            return byTransfers;
        }
        return Long.compare(left.route().routeId(), right.route().routeId());
    }

    /**
     * Min/max span of one attribute across the candidate set, with the
     * zero-span case handled once rather than at every call site.
     */
    private record Range(long min, long max) {

        static Range ofLongs(List<RouteCandidate> candidates,
                             java.util.function.ToLongFunction<RouteCandidate> extractor) {
            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;
            for (RouteCandidate candidate : candidates) {
                long value = extractor.applyAsLong(candidate);
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            return new Range(min, max);
        }

        static Range ofInts(List<RouteCandidate> candidates,
                            java.util.function.ToIntFunction<RouteCandidate> extractor) {
            return ofLongs(candidates, candidate -> extractor.applyAsInt(candidate));
        }

        /** Returns 0 when every route shares the same value — see the class javadoc. */
        double normalize(long value) {
            if (max == min) {
                return 0.0;
            }
            return (double) (value - min) / (double) (max - min);
        }
    }
}
