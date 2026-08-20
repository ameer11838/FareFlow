package com.fareflow.discovery;

import com.fareflow.discovery.dto.JourneyOptionDto;
import com.fareflow.discovery.dto.JourneySearchResponse;
import com.fareflow.fare.FareCalculation;
import com.fareflow.fare.FareStatus;
import com.fareflow.fare.UserFareContext;
import com.fareflow.journey.Journey;
import com.fareflow.journey.JourneyCandidateAdapter;
import com.fareflow.journey.JourneyLeg;
import com.fareflow.location.LocationCandidate;
import com.fareflow.location.LocationService;
import com.fareflow.recommendation.dto.ProfileDto;
import com.fareflow.recommendation.dto.WeightsDto;
import com.fareflow.recommendation.optimization.*;
import com.fareflow.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The full pipeline: geocode, discover, price, score, explain.
 *
 * <p>Every step is a separate collaborator, and the optimization engine at the end
 * is the same pure scorer that ranked the seeded routes. Journeys reach it through
 * {@link JourneyCandidateAdapter}, so multi-leg itineraries required no change to
 * scoring at all.
 */
@Service
@Transactional(readOnly = true)
public class JourneyRecommendationService {

    private final LocationService locationService;
    private final JourneyPlanningService planningService;
    private final RouteScorer routeScorer;
    private final ExplanationBuilder explanationBuilder;
    private final PreferenceResolver preferenceResolver;

    public JourneyRecommendationService(LocationService locationService,
                                        JourneyPlanningService planningService,
                                        RouteScorer routeScorer,
                                        ExplanationBuilder explanationBuilder,
                                        PreferenceResolver preferenceResolver) {
        this.locationService = locationService;
        this.planningService = planningService;
        this.routeScorer = routeScorer;
        this.explanationBuilder = explanationBuilder;
        this.preferenceResolver = preferenceResolver;
    }

    public JourneySearchResponse search(String originQuery,
                                        String destinationQuery,
                                        PreferenceContext context,
                                        UserFareContext fareContext) {

        LocationCandidate origin = locationService.resolve(originQuery)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Could not find a place matching '%s'".formatted(originQuery)));
        LocationCandidate destination = locationService.resolve(destinationQuery)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Could not find a place matching '%s'".formatted(destinationQuery)));

        OptimizationWeights weights = preferenceResolver.resolve(context);
        ProfileDto profile = ProfileDto.from(context.profile());
        WeightsDto weightsDto = WeightsDto.from(weights);

        List<JourneyPlanningService.PricedJourney> priced =
                planningService.plan(origin, destination, fareContext);

        if (priced.isEmpty()) {
            return new JourneySearchResponse(origin, destination, profile, weightsDto,
                    "FareFlow does not have transit coverage between these places yet.",
                    null, List.of(),
                    List.of("No journeys found. FareFlow's network currently covers the "
                            + "Philadelphia–New York corridor."));
        }

        // Index by synthetic id so scored results can be mapped back to journeys.
        Map<Long, JourneyPlanningService.PricedJourney> byId = new HashMap<>();
        List<RouteCandidate> candidates = new ArrayList<>();
        long nextId = 1;
        for (JourneyPlanningService.PricedJourney entry : priced) {
            byId.put(nextId, entry);
            candidates.add(JourneyCandidateAdapter.toCandidate(nextId, entry.journey(), entry.fare()));
            nextId++;
        }

        List<ScoredRoute> ranked = routeScorer.score(candidates, weights);
        Map<Long, String> explanations = explanationBuilder.explain(ranked);
        String summary = explanationBuilder.summarize(ranked);
        String contextNote = buildContextNote(candidates, context, ranked);

        List<JourneyOptionDto> options = trimKeepingLabelled(ranked).stream()
                .map(scored -> toDto(scored, byId.get(scored.route().routeId()), explanations))
                .toList();

        return new JourneySearchResponse(origin, destination, profile, weightsDto,
                summary, contextNote, options, notices(priced));
    }

    /** How many options to show. More than this is noise on a results panel. */
    private static final int MAX_DISPLAYED = 5;

    /**
     * Trims to a readable number while guaranteeing every labelled option survives.
     *
     * <p>A plain {@code limit()} could drop the cheapest journey when several
     * faster ones score above it, leaving the panel without a CHEAPEST option at
     * all. Ranking order is preserved.
     */
    private static List<ScoredRoute> trimKeepingLabelled(List<ScoredRoute> ranked) {
        if (ranked.size() <= MAX_DISPLAYED) {
            return ranked;
        }

        java.util.LinkedHashSet<ScoredRoute> kept = new java.util.LinkedHashSet<>();
        // Labelled options first, in rank order, so cheapest/fastest always appear.
        ranked.stream().filter(route -> !route.labels().isEmpty()).forEach(kept::add);
        for (ScoredRoute route : ranked) {
            if (kept.size() >= MAX_DISPLAYED) {
                break;
            }
            kept.add(route);
        }

        // Restore ranking order: the set was filled labelled-first.
        return ranked.stream().filter(kept::contains).toList();
    }

    /** Surfaces honest caveats rather than burying them. */
    private static List<String> notices(List<JourneyPlanningService.PricedJourney> priced) {
        List<String> notices = new ArrayList<>();

        boolean anyUnknown = priced.stream().anyMatch(entry -> !entry.fare().isPriced());
        if (anyUnknown) {
            notices.add("Some options have no published fare FareFlow can compute "
                    + "(for example Amtrak, which is priced dynamically). Those are shown "
                    + "without a fare rather than with a guess.");
        }

        boolean anyEstimated = priced.stream()
                .anyMatch(entry -> entry.fare().status() == FareStatus.ESTIMATED);
        if (anyEstimated) {
            notices.add("Commuter rail fares are estimated from distance bands rather "
                    + "than a full zone tariff.");
        }

        notices.add("Journey times are typical scheduled durations, not live departures.");
        return notices;
    }

    private String buildContextNote(List<RouteCandidate> candidates,
                                    PreferenceContext context,
                                    List<ScoredRoute> ranked) {
        if (context.profile() == ContextProfile.BALANCED || ranked.isEmpty()) {
            return null;
        }
        PreferenceContext balanced = new PreferenceContext(
                context.userId(), context.weeklyBudgetCents(), context.spentCentsThisWeek(),
                ContextProfile.BALANCED);
        List<ScoredRoute> balancedRanking =
                routeScorer.score(candidates, preferenceResolver.resolve(balanced));
        if (balancedRanking.isEmpty()) {
            return null;
        }
        return explanationBuilder.contextNote(
                context.profile(), ranked.getFirst(), balancedRanking.getFirst());
    }

    private static JourneyOptionDto toDto(ScoredRoute scored,
                                          JourneyPlanningService.PricedJourney entry,
                                          Map<Long, String> explanations) {
        Journey journey = entry.journey();
        FareCalculation fare = entry.fare();

        return new JourneyOptionDto(
                journey.id(),
                journey.summary(),
                journey.totalMinutes(),
                journey.walkingMinutes(),
                journey.transfers(),
                fare.isPriced() ? fare.totalFareCents() : null,
                fare.status().name(),
                fare.source().name(),
                fare.explanationLines(),
                scored.labels().stream().map(RecommendationLabel::name).sorted().toList(),
                scored.hasLabel(RecommendationLabel.BEST_VALUE),
                scored.score(),
                // An unpriced journey must not claim a money comparison it cannot make.
                fare.isPriced()
                        ? explanations.getOrDefault(scored.route().routeId(), "")
                        : "This option has no published fare FareFlow can compute, so it is "
                          + "not compared on cost.",
                journey.dataSource(),
                journey.legs().stream().map(JourneyRecommendationService::toLegDto).toList());
    }

    private static JourneyOptionDto.LegDto toLegDto(JourneyLeg leg) {
        return new JourneyOptionDto.LegDto(
                leg.mode().name(),
                leg.agency(),
                leg.lineName(),
                leg.fromStopName(),
                leg.toStopName(),
                leg.durationMinutes(),
                leg.waitMinutes(),
                leg.waypoints().stream()
                        .map(point -> new JourneyOptionDto.WaypointDto(
                                point.name(), point.latitude(), point.longitude()))
                        .toList());
    }
}
