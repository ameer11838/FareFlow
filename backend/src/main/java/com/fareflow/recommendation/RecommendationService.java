package com.fareflow.recommendation;

import com.fareflow.recommendation.dto.ProfileDto;
import com.fareflow.recommendation.dto.RecommendationResponse;
import com.fareflow.recommendation.dto.RecommendedRouteDto;
import com.fareflow.recommendation.dto.RouteComparisonDto;
import com.fareflow.recommendation.dto.RouteGeometryDto;
import com.fareflow.recommendation.dto.ScoreBreakdownDto;
import com.fareflow.recommendation.dto.WeightsDto;
import com.fareflow.recommendation.optimization.ContextProfile;
import com.fareflow.recommendation.optimization.ExplanationBuilder;
import com.fareflow.recommendation.optimization.OptimizationWeights;
import com.fareflow.recommendation.optimization.PreferenceContext;
import com.fareflow.recommendation.optimization.PreferenceResolver;
import com.fareflow.recommendation.optimization.RecommendationLabel;
import com.fareflow.recommendation.optimization.RouteCandidate;
import com.fareflow.recommendation.optimization.RouteScorer;
import com.fareflow.recommendation.optimization.ScoredRoute;
import com.fareflow.route.provider.TransitRouteCatalog;
import com.fareflow.route.provider.TransitRouteData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrates a route search: fetch candidates, resolve weights, score, explain.
 *
 * <p>Holds no algorithm of its own. All the arithmetic lives in the pure
 * {@code optimization} package; this class connects that engine to a route source
 * and to the HTTP layer.
 */
@Service
@Transactional(readOnly = true)
public class RecommendationService {

    private final TransitRouteCatalog routeCatalog;
    private final RouteScorer routeScorer;
    private final ExplanationBuilder explanationBuilder;
    private final PreferenceResolver preferenceResolver;

    public RecommendationService(TransitRouteCatalog routeCatalog,
                                 RouteScorer routeScorer,
                                 ExplanationBuilder explanationBuilder,
                                 PreferenceResolver preferenceResolver) {
        this.routeCatalog = routeCatalog;
        this.routeScorer = routeScorer;
        this.explanationBuilder = explanationBuilder;
        this.preferenceResolver = preferenceResolver;
    }

    /**
     * @param context              stance plus budget information for weight resolution
     * @param remainingBudgetCents null when unknown — used only to flag over-budget fares
     */
    public RecommendationResponse recommend(String origin,
                                            String destination,
                                            PreferenceContext context,
                                            Long remainingBudgetCents) {

        OptimizationWeights weights = preferenceResolver.resolve(context);
        ContextProfile profile = context.profile();
        ProfileDto profileDto = ProfileDto.from(profile);
        WeightsDto weightsDto = WeightsDto.from(weights);

        List<TransitRouteData> routes = routeCatalog.findRoutes(origin, destination);
        if (routes.isEmpty()) {
            return RecommendationResponse.empty(origin, destination, profileDto, weightsDto,
                    "No routes are available between %s and %s.".formatted(origin, destination));
        }

        List<RouteCandidate> candidates = routes.stream()
                .map(TransitRouteData::toCandidate)
                .toList();

        // Geometry is looked up by route id at the DTO boundary. The scorer never
        // sees it -- shape has no bearing on which route is the best value.
        Map<Long, RouteGeometryDto> geometryByRoute = routes.stream()
                .collect(java.util.stream.Collectors.toMap(
                        TransitRouteData::routeId, RouteGeometryDto::from, (first, second) -> first));

        List<ScoredRoute> ranked = routeScorer.score(candidates, weights);
        Map<Long, String> explanations = explanationBuilder.explain(ranked);
        String summary = explanationBuilder.summarize(ranked);
        String contextNote = buildContextNote(candidates, context, profile, ranked);

        Optional<ScoredRoute> fastest = withLabel(ranked, RecommendationLabel.FASTEST);
        Optional<ScoredRoute> bestValue = withLabel(ranked, RecommendationLabel.BEST_VALUE);
        Optional<ScoredRoute> cheapest = withLabel(ranked, RecommendationLabel.CHEAPEST);

        List<RecommendedRouteDto> options = ranked.stream()
                .map(scored -> toDto(scored, explanations, remainingBudgetCents, fastest, bestValue, cheapest, geometryByRoute))
                .toList();

        return new RecommendationResponse(origin, destination, profileDto, weightsDto,
                summary, contextNote, options);
    }

    /**
     * Explains a profile-driven change by scoring the same candidates a second time
     * under BALANCED and describing the difference. Returns null when the profile did
     * not change the winner — saying "your choice made no difference" is noise.
     */
    private String buildContextNote(List<RouteCandidate> candidates,
                                    PreferenceContext context,
                                    ContextProfile profile,
                                    List<ScoredRoute> ranked) {
        if (profile == ContextProfile.BALANCED || ranked.isEmpty()) {
            return null;
        }

        PreferenceContext balancedContext = new PreferenceContext(
                context.userId(), context.weeklyBudgetCents(), context.spentCentsThisWeek(),
                ContextProfile.BALANCED);

        List<ScoredRoute> balancedRanking =
                routeScorer.score(candidates, preferenceResolver.resolve(balancedContext));
        if (balancedRanking.isEmpty()) {
            return null;
        }

        return explanationBuilder.contextNote(profile, ranked.getFirst(), balancedRanking.getFirst());
    }

    /**
     * Finds the fare of the fastest route for a pair, used as the "saved vs. fastest
     * route" baseline. Empty when fewer than two routes exist, because a user with no
     * alternative made no choice and no honest savings figure exists.
     */
    public Optional<Long> findFastestFareBaseline(String origin, String destination) {
        List<TransitRouteData> routes = routeCatalog.findRoutes(origin, destination);
        if (routes.size() < 2) {
            return Optional.empty();
        }
        return routes.stream()
                .min(Comparator.comparingInt(TransitRouteData::durationMinutes)
                        .thenComparingLong(TransitRouteData::fareCents)
                        .thenComparingLong(TransitRouteData::routeId))
                .map(TransitRouteData::fareCents);
    }

    private RecommendedRouteDto toDto(ScoredRoute scored,
                                      Map<Long, String> explanations,
                                      Long remainingBudgetCents,
                                      Optional<ScoredRoute> fastest,
                                      Optional<ScoredRoute> bestValue,
                                      Optional<ScoredRoute> cheapest,
                                      Map<Long, RouteGeometryDto> geometryByRoute) {
        RouteCandidate route = scored.route();
        boolean overBudget = remainingBudgetCents != null && route.fareCents() > remainingBudgetCents;

        return new RecommendedRouteDto(
                route.routeId(),
                route.provider(),
                route.providerDisplayName(),
                route.mode(),
                route.durationMinutes(),
                route.fareCents(),
                route.transfers(),
                scored.labels().stream().map(RecommendationLabel::name).sorted().toList(),
                scored.hasLabel(RecommendationLabel.BEST_VALUE),
                scored.score(),
                ScoreBreakdownDto.from(scored.breakdown()),
                overBudget,
                explanations.getOrDefault(route.routeId(), ""),
                comparisonTo(scored, fastest),
                comparisonTo(scored, bestValue),
                comparisonTo(scored, cheapest),
                geometryByRoute.get(route.routeId()));
    }

    /** Null when there is no reference or the reference is this same route. */
    private static RouteComparisonDto comparisonTo(ScoredRoute subject, Optional<ScoredRoute> reference) {
        if (reference.isEmpty() || reference.get().route().routeId() == subject.route().routeId()) {
            return null;
        }
        RouteCandidate other = reference.get().route();
        return new RouteComparisonDto(
                other.routeId(),
                other.providerDisplayName(),
                subject.route().fareCents() - other.fareCents(),
                subject.route().durationMinutes() - other.durationMinutes(),
                subject.route().transfers() - other.transfers());
    }

    private static Optional<ScoredRoute> withLabel(List<ScoredRoute> ranked, RecommendationLabel label) {
        return ranked.stream().filter(scored -> scored.hasLabel(label)).findFirst();
    }
}
