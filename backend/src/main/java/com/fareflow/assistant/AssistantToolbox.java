package com.fareflow.assistant;

import com.fareflow.budget.BudgetService;
import com.fareflow.budget.WeeklySummary;
import com.fareflow.assistant.dto.AssistantPageContext;
import com.fareflow.discovery.JourneyRecommendationService;
import com.fareflow.discovery.dto.JourneyOptionDto;
import com.fareflow.discovery.dto.JourneySearchResponse;
import com.fareflow.fare.UserFareContext;
import com.fareflow.insights.HistoryRange;
import com.fareflow.insights.InsightsService;
import com.fareflow.insights.SpendingHistoryService;
import com.fareflow.passes.PassOptimizationService;
import com.fareflow.profile.TravelProfileService;
import com.fareflow.profile.UserTravelProfile;
import com.fareflow.profile.dto.TravelProfileResponse;
import com.fareflow.recommendation.optimization.ContextProfile;
import com.fareflow.recommendation.optimization.PreferenceContext;
import com.fareflow.trip.Trip;
import com.fareflow.trip.TripRepository;
import com.fareflow.trip.TripStatus;
import com.fareflow.trip.dto.TripResponse;
import com.fareflow.user.User;
import com.fareflow.session.TransitSessionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.FunctionDeclaration;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The assistant's only source of facts.
 *
 * <p>This is the guardrail that makes an LLM safe to put in front of money. The
 * model has no numbers of its own: every fare, budget figure, duration, station,
 * and operator it can state has to arrive through one of these tools, and each one
 * returns the output of the same deterministic services the rest of FareFlow uses.
 * If the engine cannot derive something, the tool says so, and the model's
 * instructions require it to relay that rather than fill the gap.
 *
 * <p>Nothing here takes a user id from the model. The authenticated rider is passed
 * in by the service, so a prompt-injected "look up user 7" has nothing to attach to.
 */
@Component
public class AssistantToolbox {

    /**
     * What a tool call produced.
     *
     * @param routes set only by {@code plan_journey} — the real, priced search
     *               result, handed back to the client so the map and route cards
     *               show exactly what the model was shown
     */
    public record Outcome(String json, boolean isError, JourneySearchResponse routes,
                          List<TripResponse> trips) {

        static Outcome ok(String json) {
            return new Outcome(json, false, null, List.of());
        }

        static Outcome ok(String json, JourneySearchResponse routes) {
            return new Outcome(json, false, routes, List.of());
        }

        static Outcome trips(String json, List<TripResponse> trips) {
            return new Outcome(json, false, null, trips);
        }

        static Outcome error(String message) {
            return new Outcome(message, true, null, List.of());
        }
    }

    private final ObjectMapper objectMapper;
    private final BudgetService budgetService;
    private final InsightsService insightsService;
    private final SpendingHistoryService spendingHistoryService;
    private final TravelProfileService travelProfileService;
    private final TripRepository tripRepository;
    private final PassOptimizationService passOptimizationService;
    private final JourneyRecommendationService journeyRecommendationService;
    private final TransitSessionService transitSessionService;
    private final Clock clock;

    public AssistantToolbox(ObjectMapper objectMapper,
                            BudgetService budgetService,
                            InsightsService insightsService,
                            SpendingHistoryService spendingHistoryService,
                            TravelProfileService travelProfileService,
                            TripRepository tripRepository,
                            PassOptimizationService passOptimizationService,
                            JourneyRecommendationService journeyRecommendationService,
                            TransitSessionService transitSessionService,
                            Clock clock) {
        this.objectMapper = objectMapper;
        this.budgetService = budgetService;
        this.insightsService = insightsService;
        this.spendingHistoryService = spendingHistoryService;
        this.travelProfileService = travelProfileService;
        this.tripRepository = tripRepository;
        this.passOptimizationService = passOptimizationService;
        this.journeyRecommendationService = journeyRecommendationService;
        this.transitSessionService = transitSessionService;
        this.clock = clock;
    }

    // ------------------------------------------------------------ definitions

    public List<FunctionDeclaration> tools() {
        return List.of(
                tool("get_budget_status",
                        """
                        The rider's transportation budget for the current week: weekly budget, \
                        amount spent so far, amount remaining, percent used, trips taken, and how \
                        many days are left in the week. Call this for any question about whether \
                        they can afford something, how much is left, or how they are pacing. \
                        weeklyBudgetCents is null when the rider has not set a budget — that means \
                        no budget exists, not a budget of zero.""",
                        Map.of(), List.of()),

                tool("get_weekly_insights",
                        """
                        Derived intelligence for the current week: average fare, average trip \
                        duration, spend split by operator, money saved versus always taking the \
                        fastest route, minutes traded for those savings, and a personalised block \
                        based on the rider's onboarding answers (commute frequency, projected \
                        weekly spend, budget buffer, pass suggestion). Null fields mean FareFlow \
                        could not derive the figure yet.""",
                        Map.of(), List.of()),

                tool("get_spending_history",
                        """
                        Travel and spending over a longer window, bucketed for trend questions: \
                        per-period spend and trip counts, totals, a comparison against the \
                        preceding window of the same length, and breakdowns by operator and mode. \
                        Use this for "this month versus last", "how has my commute changed", or \
                        anything about a trend. If hasData is false there is genuinely no travel \
                        in that window — say so rather than describing a trend.""",
                        Map.of("range", Map.of(
                                "type", "string",
                                "enum", List.of("7d", "30d", "3m", "1y"),
                                "description", "Window to summarise. Defaults to 30d.")),
                        List.of()),

                tool("get_month_to_date_spending",
                        """
                        Exact transportation spending from the first day of the rider's current \
                        calendar month through right now, in the rider's timezone. Returns the \
                        real completed-trip count and sum in integer cents. Use this for "how much \
                        have I spent this month?" rather than treating the last 30 days as the \
                        current month.""",
                        Map.of(), List.of()),

                tool("get_travel_profile",
                        """
                        What the rider told FareFlow during onboarding: their default trip \
                        priority, typical origin and destination, how often they commute, which \
                        transit modes they prefer, whether they hold a transit pass, and their \
                        weekly budget. Call this before planning a journey when the rider refers \
                        to a place by habit ("class", "work", "home") rather than by name.""",
                        Map.of(), List.of()),

                tool("get_recent_trips",
                        """
                        The rider's actual completed trips with origin, \
                        destination, operator, mode, fare, duration, transfers, and when it was \
                        taken. Use RECENT for what they have been doing and CHEAPEST when the rider \
                        asks to see their cheapest trips. These records are also returned to the \
                        client as links; do not invent or alter them.""",
                        Map.of(
                                "limit", Map.of(
                                        "type", "integer",
                                        "description", "How many trips to return, 1 to 20. Defaults to 5."),
                                "sort", Map.of(
                                        "type", "string",
                                        "enum", List.of("RECENT", "CHEAPEST"),
                                        "description", "Defaults to RECENT.")),
                        List.of()),

                tool("get_pass_recommendation",
                        """
                        Whether a transit pass would beat paying per ride, computed from the \
                        rider's actual travel. Returns the recommended pass and the saving, or a \
                        recommendation to keep paying per ride when no pass wins.""",
                        Map.of(), List.of()),

                tool("get_current_route_search",
                        """
                        Reruns the route search currently visible in FareFlow and returns the \
                        authoritative ranked routes plus the selected journey id. Use this before \
                        explaining the selected route or handling a route follow-up. It fails \
                        plainly when there is no active route search on the current page.""",
                        Map.of(), List.of()),

                tool("get_active_transit_session",
                        """
                        The rider's current FareFlow transit session, including its selected route, \
                        current and next known stop, recorded stops, route-derived distance, elapsed \
                        time, current simulated fare, final fare when ended, and whether it can be \
                        advanced, ended, or paid. Call this for questions about an active trip, a \
                        usage fare, a bus that did not arrive, or why the current trip costs what it \
                        does. If no session is open, say so. This is read-only and never changes or \
                        pays for the trip.""",
                        Map.of(), List.of()),

                tool("plan_journey",
                        """
                        Plans real public-transit journeys between two places and prices them. \
                        This is the same planner the Plan page uses: it returns several ranked \
                        options with total time, fare, walking minutes, transfers, a fare \
                        breakdown, and the individual legs with their lines and stations. Call it \
                        for any question about how to get somewhere, what a trip would cost, or \
                        which option is cheapest or fastest.

                        Omit origin or destination to use the rider's typical places from their \
                        travel profile. A journey with a null fareCents could not be priced — say \
                        the fare is unavailable rather than guessing one. The options you receive \
                        are also shown to the rider on the map, so refer to them by their summary.""",
                        Map.of(
                                "origin", Map.of("type", "string",
                                        "description", "Where the journey starts. Omit to use the rider's typical origin."),
                                "destination", Map.of("type", "string",
                                        "description", "Where the journey ends. Omit to use the rider's typical destination."),
                                "priority", Map.of(
                                        "type", "string",
                                        "enum", List.of("BALANCED", "RUSH", "SAVE_MONEY", "FEWER_TRANSFERS"),
                                        "description", """
                                                How to rank the options. SAVE_MONEY for cheapest, RUSH for \
                                                fastest, FEWER_TRANSFERS for the simplest trip. Omit to use \
                                                the rider's saved default."""),
                                "maxFareCents", Map.of(
                                        "type", "integer",
                                        "description", "Optional hard maximum fare in integer cents. For $5 pass 500. Java filters the real priced options.")),
                        List.of()));
    }

    private static FunctionDeclaration tool(String name, String description,
                                            Map<String, Object> properties, List<String> required) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        if (!required.isEmpty()) {
            parameters.put("required", required);
        }

        return FunctionDeclaration.builder()
                .name(name)
                .description(description)
                .parametersJsonSchema(parameters)
                .build();
    }

    // -------------------------------------------------------------- dispatch

    /**
     * Runs one tool for the authenticated rider.
     *
     * <p>An unknown tool name or a failing lookup comes back as an error outcome
     * rather than an exception: the model is told the call failed and can say so,
     * which is a better answer than a 500 in the middle of a conversation.
     */
    public Outcome invoke(String name, Map<String, Object> input, User user,
                          AssistantPageContext pageContext) {
        try {
            return switch (name) {
                case "get_budget_status" -> Outcome.ok(json(budgetStatus(user)));
                case "get_weekly_insights" -> Outcome.ok(json(insightsService.forCurrentWeek(user)));
                case "get_spending_history" -> Outcome.ok(json(spendingHistoryService.history(
                        user, HistoryRange.parse(string(input, "range")).orElse(HistoryRange.defaultRange()))));
                case "get_month_to_date_spending" -> Outcome.ok(json(monthToDateSpending(user)));
                case "get_travel_profile" -> Outcome.ok(json(travelProfile(user)));
                case "get_recent_trips" -> recentTripsOutcome(user, input);
                case "get_pass_recommendation" -> Outcome.ok(json(passOptimizationService.recommendFor(user)));
                case "get_current_route_search" -> currentRouteSearch(user, pageContext);
                case "get_active_transit_session" -> Outcome.ok(json(
                        transitSessionService.active(user).orElse(null)));
                case "plan_journey" -> planJourney(user, input, pageContext);
                default -> Outcome.error("Unknown tool: " + name);
            };
        } catch (Exception exception) {
            // Deliberately broad: a tool failure must degrade to "I could not look
            // that up" inside the conversation, never to a failed HTTP request.
            return Outcome.error("This lookup failed: "
                    + (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
        }
    }

    private Map<String, Object> budgetStatus(User user) {
        WeeklySummary summary = budgetService.currentWeek(user.getId());
        LocalDate today = LocalDate.ofInstant(clock.instant(), user.zoneId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("weekStartDate", summary.week().weekStartDate().toString());
        payload.put("today", today.toString());
        // Monday is day 1, so seven minus that is how many days are still ahead,
        // counting today. The assistant needs this for "with N days left" answers.
        payload.put("daysLeftInWeekIncludingToday", 8 - today.getDayOfWeek().getValue());
        payload.put("weeklyBudgetCents", summary.weeklyBudgetCents());
        payload.put("hasBudget", summary.hasBudget());
        payload.put("spentCents", summary.spentCents());
        payload.put("remainingCents", summary.remainingCents());
        payload.put("budgetUtilization", summary.budgetUtilization());
        payload.put("tripCount", summary.tripCount());
        payload.put("savedVersusFastestCents", summary.savedVersusFastestCents());
        payload.put("currency", "USD");
        payload.put("note", "All amounts are integer cents. Divide by 100 for dollars.");
        return payload;
    }

    private Object travelProfile(User user) {
        UserTravelProfile profile = travelProfileService.find(user.getId()).orElse(null);
        if (profile == null) {
            return Map.of(
                    "hasProfile", false,
                    "message", "This rider has not completed onboarding, so FareFlow knows "
                            + "nothing about their typical trips or preferences.");
        }
        return Map.of(
                "hasProfile", true,
                "profile", TravelProfileResponse.from(profile, user.getWeeklyBudgetCents()));
    }

    private Map<String, Object> monthToDateSpending(User user) {
        ZonedDateTime now = clock.instant().atZone(user.zoneId());
        Instant start = now.withDayOfMonth(1).toLocalDate().atStartOfDay(user.zoneId()).toInstant();
        List<Trip> trips = tripRepository.findCompletedBetween(user.getId(), start, clock.instant());
        long spentCents = trips.stream().mapToLong(Trip::getFareCents).sum();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("startDate", start.atZone(user.zoneId()).toLocalDate().toString());
        payload.put("through", now.toString());
        payload.put("tripCount", trips.size());
        payload.put("spentCents", spentCents);
        payload.put("hasData", !trips.isEmpty());
        payload.put("currency", "USD");
        payload.put("note", "spentCents is the sum of completed stored trips in this calendar month.");
        return payload;
    }

    private List<TripResponse> recentTrips(User user, int limit) {
        int bounded = Math.clamp(limit, 1, 20);
        return tripRepository
                .findByUserIdAndStatusOrderByTakenAtDescIdDesc(
                        user.getId(), TripStatus.COMPLETED, PageRequest.of(0, bounded))
                .getContent().stream()
                .map(TripResponse::from)
                .toList();
    }

    private Outcome recentTripsOutcome(User user, Map<String, Object> input) {
        int bounded = Math.clamp(integer(input, "limit", 5), 1, 20);
        String sort = string(input, "sort");
        List<TripResponse> trips;
        if ("CHEAPEST".equalsIgnoreCase(sort)) {
            trips = new ArrayList<>(recentTrips(user, 20));
            trips.sort(Comparator.comparingLong(TripResponse::fareCents)
                    .thenComparing(TripResponse::takenAt, Comparator.reverseOrder()));
            trips = trips.stream().limit(bounded).toList();
        } else {
            trips = recentTrips(user, bounded);
        }
        return Outcome.trips(json(trips), trips);
    }

    private Outcome currentRouteSearch(User user, AssistantPageContext pageContext) {
        AssistantPageContext.ActiveRouteSearch search = pageContext == null
                ? null : pageContext.activeRouteSearch();
        if (search == null || !search.isSearchable()) {
            return Outcome.error("There is no active route search on the rider's current page.");
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("origin", search.origin());
        input.put("destination", search.destination());
        if (!isBlank(search.profile())) input.put("priority", search.profile());
        Outcome planned = planJourney(user, input, pageContext);
        if (planned.isError() || planned.routes() == null) return planned;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("selectedJourneyId", search.selectedJourneyId());
        payload.put("search", planned.routes());
        return Outcome.ok(json(payload), planned.routes());
    }

    /**
     * Runs the real planner and hands the result to both the model and the client.
     *
     * <p>Falls back to the rider's typical origin and destination when the question
     * referred to them by habit. When neither the question nor the profile supplies
     * a place, that is reported as a missing input rather than invented.
     */
    private Outcome planJourney(User user, Map<String, Object> input,
                                AssistantPageContext pageContext) {
        UserTravelProfile profile = travelProfileService.find(user.getId()).orElse(null);

        String origin = string(input, "origin");
        String destination = string(input, "destination");

        AssistantPageContext.ActiveRouteSearch active = pageContext == null
                ? null : pageContext.activeRouteSearch();
        if (isBlank(origin) && active != null) origin = active.origin();
        if (isBlank(destination) && active != null) destination = active.destination();

        if (isBlank(origin) && profile != null && profile.getTypicalOrigin() != null) {
            origin = profile.getTypicalOrigin().name();
        }
        if (isBlank(destination) && profile != null && profile.getTypicalDestination() != null) {
            destination = profile.getTypicalDestination().name();
        }
        if (isBlank(origin) || isBlank(destination)) {
            return Outcome.error("A journey needs both an origin and a destination, and this rider "
                    + "has no saved typical places to fall back on. Ask them where they are "
                    + "travelling from and to.");
        }

        ContextProfile priority =
                travelProfileService.resolveContextProfile(string(input, "priority"), user.getId());
        WeeklySummary summary = budgetService.currentWeek(user.getId());

        PreferenceContext preference = new PreferenceContext(
                user.getId(), summary.weeklyBudgetCents(), summary.spentCents(), priority);
        UserFareContext fareContext = new UserFareContext(Math.max(0, summary.spentCents()), 0, Set.of());

        JourneySearchResponse result = journeyRecommendationService.search(
                origin.trim(), destination.trim(), preference, fareContext);

        int maxFareCents = integer(input, "maxFareCents", -1);
        if (maxFareCents >= 0) {
            result = applyFareLimit(result, maxFareCents);
        }

        return Outcome.ok(json(result), result);
    }

    static JourneySearchResponse applyFareLimit(JourneySearchResponse result, int maxFareCents) {
        List<JourneyOptionDto> eligible = result.options().stream()
                .filter(option -> option.fareCents() != null && option.fareCents() <= maxFareCents)
                .toList();
        List<JourneyOptionDto> reranked = new ArrayList<>(eligible.size());
        for (int index = 0; index < eligible.size(); index++) {
            JourneyOptionDto option = eligible.get(index);
            reranked.add(new JourneyOptionDto(
                    option.journeyId(), option.summary(), option.totalMinutes(), option.walkingMinutes(),
                    option.transfers(), option.fareCents(), option.fareStatus(), option.fareSource(),
                    option.fareBreakdown(), option.labels(), index == 0, option.score(),
                    option.explanation(), option.dataSource(), option.usageFareMinCents(),
                    option.usageFareMaxCents(), option.usagePricingVersion(), option.legs()));
        }
        List<String> notices = new ArrayList<>(result.notices());
        notices.add(reranked.isEmpty()
                ? "No priced route met the requested fare limit."
                : "Only routes at or below the requested fare limit are shown.");
        return new JourneySearchResponse(
                result.origin(), result.destination(), result.profile(), result.weightsUsed(),
                result.budgetContext(), result.summary(), result.contextNote(), reranked,
                List.copyOf(notices));
    }

    // --------------------------------------------------------------- helpers

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialise a tool result", exception);
        }
    }

    private static String string(Map<String, Object> input, String key) {
        Object value = input.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static int integer(Map<String, Object> input, String key, int fallback) {
        Object value = input.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
