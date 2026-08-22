package com.fareflow.assistant;

import com.fareflow.assistant.dto.AskRequest;
import com.fareflow.assistant.dto.AssistantConfigResponse;
import com.fareflow.assistant.dto.AssistantResponse;
import com.fareflow.assistant.dto.AssistantTurn;
import com.fareflow.assistant.dto.AssistantPageContext;
import com.fareflow.discovery.dto.JourneySearchResponse;
import com.fareflow.profile.TravelProfileService;
import com.fareflow.profile.UserTravelProfile;
import com.fareflow.user.User;
import com.fareflow.trip.dto.TripResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.AutomaticFunctionCallingConfig;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ask FareFlow: a Gemini-backed assistant that can only speak in FareFlow's own numbers.
 *
 * <p>The design constraint here is narrow and load-bearing. A language model that
 * improvises a dollar amount inside a budgeting app is worse than no assistant at
 * all, so the model is given no figures in its prompt and no arithmetic to do —
 * only tools that return the output of the same deterministic engines the Wallet,
 * Insights, and Plan pages read from. The system prompt then forbids stating any
 * number that did not arrive that way.
 *
 * <p>The conversation is not stored. The Gemini API is stateless and FareFlow
 * keeps no chat history; the thread lives in the rider's browser tab and is
 * replayed with each question.
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    /**
     * The rules that make the assistant safe to ship.
     *
     * <p>Stable text, kept free of per-request values so it stays a cacheable
     * prefix; everything that changes per rider or per day goes in the user turn.
     */
    private static final String SYSTEM_PROMPT = """
            You are Ask FareFlow, the assistant inside FareFlow — a public-transit trip \
            planner and transportation-budgeting app. You help riders plan transit journeys \
            and understand what they spend getting around.

            # Product scope

            FareFlow plans and tracks public transit only: train, subway, bus, and ferry. \
            Walking may appear only to connect the rider to, from, or between transit legs. \
            Never recommend, compare, or budget for cars, taxis, Uber, Lyft, flights, bicycles, \
            or bike routing. If asked, say FareFlow covers public transit only and offer to find \
            a train, subway, bus, or ferry journey instead.

            You may explain payment, wallet, budget, and ledger facts returned by read-only tools. \
            You must never create, authorize, settle, retry, or refund a payment; create a trip; \
            or modify a balance, budget, or ledger. Never claim that you completed a financial \
            action. Those actions require deterministic FareFlow services and explicit rider UI.

            # The rule that matters most

            You have no knowledge of this rider's money, travel, or transit network. Every \
            fare, budget figure, travel time, walking time, transfer count, date, station \
            name, line name, and operator name you state must come from a tool result in \
            this conversation, verbatim.

            - If you have not called a tool that returns a figure, you do not know it. Call \
              the tool, or say you cannot answer.
            - Never estimate, extrapolate, average, or reason your way to a dollar amount or \
              a duration. Do not do arithmetic on figures to produce a new figure the tools \
              did not return — if a rider asks something the tools cannot answer directly, \
              say which part you can answer and which part you cannot.
            - A null field means FareFlow could not derive that value. Say so plainly \
              ("you haven't set a weekly budget yet", "that route couldn't be priced"). \
              Never substitute zero for null.
            - If a tool reports no data for a period, say there is no data for it. Do not \
              describe what the trend probably looks like.
            - Amounts from tools are integer cents. Convert to dollars for display — 1250 is \
              $12.50 — and never round beyond that.

            # Transit data availability

            Only state schedules, live delays, headsigns, stop counts, station details, or \
            accessibility information when a route lookup explicitly returns them. FareFlow \
            uses GTFS and GTFS-Realtime where configured, but coverage varies by agency and \
            region. A missing field means the provider did not supply it; say so plainly. \
            Never invent a departure, schedule, alert, stop, line, or real-time status.

            # How to answer

            Lead with the answer, then the reason. Two to four sentences is usually right; a \
            comparison of options can be a short list. Write like a knowledgeable friend who \
            respects the rider's time — no preamble, no restating the question, no bullet \
            lists of caveats.

            When the rider asks how to get somewhere, call plan_journey and describe the \
            options that come back. The rider sees those same options on the map beside your \
            answer, so refer to them by their summary and say why one fits what they asked \
            for. When they ask why a route was recommended, use the explanation and score \
            reasons the planner returned rather than composing your own. If the current page \
            has an active route search, use get_current_route_search for follow-ups such as \
            "why this route?", "I'm running late", "less walking", or "stay under $5". For \
            "I'm running late", rerun that journey with RUSH. For a spending limit, pass the \
            limit as integer cents to plan_journey so Java applies it. Never merely promise \
            that the UI was updated: call the planner so the client receives the real routes.

            For "show my cheapest trips", call get_recent_trips with sort CHEAPEST. The client \
            can open those returned trip records. You cannot initiate payment or trip changes.

            When a question is about affordability or pace, call get_budget_status first. \
            Answer the actual question ("yes, you have $18.40 left") before adding context.

            For spending in "this month", call get_month_to_date_spending. The last 30 days \
            are not the same as the current calendar month.

            Stay on FareFlow's subject: public transit, trips, routes, fares, and transportation \
            budgeting. If asked about something else, say that is outside what FareFlow \
            covers and offer a transit question you can help with instead.

            Never mention tools, JSON, fields, or that you called anything. The rider asked \
            a person-shaped question and gets a person-shaped answer.
            """;

    private final ObjectProvider<Client> clientProvider;
    private final AssistantProperties properties;
    private final AssistantToolbox toolbox;
    private final TravelProfileService travelProfileService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AssistantService(ObjectProvider<Client> clientProvider,
                            AssistantProperties properties,
                            AssistantToolbox toolbox,
                            TravelProfileService travelProfileService,
                            ObjectMapper objectMapper,
                            Clock clock) {
        this.clientProvider = clientProvider;
        this.properties = properties;
        this.toolbox = toolbox;
        this.travelProfileService = travelProfileService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    // ----------------------------------------------------------------- config

    public AssistantConfigResponse config(User user) {
        if (!properties.isUsable()) {
            return new AssistantConfigResponse(false,
                    "Ask FareFlow needs a Gemini API key. Set GEMINI_API_KEY and restart "
                            + "the server to turn it on.",
                    List.of());
        }
        return new AssistantConfigResponse(true, null, starters(user));
    }

    /**
     * Opening questions, chosen from what this rider has actually told FareFlow.
     *
     * <p>Deterministic, not model-generated: a starter chip that offers to compare
     * a commute the rider never described is a dead end before the conversation
     * begins.
     */
    private List<String> starters(User user) {
        UserTravelProfile profile = travelProfileService.find(user.getId()).orElse(null);
        List<String> starters = new ArrayList<>();

        if (profile != null && profile.getTypicalDestination() != null) {
            starters.add("What's the cheapest way to %s right now?"
                    .formatted(profile.getTypicalDestination().name()));
        } else {
            starters.add("Plan me a cheap trip into Manhattan");
        }

        starters.add(user.hasWeeklyBudget()
                ? "Can I afford my usual commute for the rest of the week?"
                : "How much have I been spending on transit?");
        starters.add("How has my commute changed this month?");
        starters.add("Would a transit pass save me money?");
        return starters;
    }

    // -------------------------------------------------------------------- ask

    public AssistantResponse ask(User user, AskRequest request) {
        Client client = clientProvider.getIfAvailable();
        if (!properties.isUsable() || client == null) {
            throw new AssistantUnavailableException(
                    "Ask FareFlow is not configured on this server. Set GEMINI_API_KEY to enable it.");
        }

        List<Content> messages = new ArrayList<>(replayHistory(request.historyOrEmpty()));
        messages.add(userMessage(context(user, request.context()) + "\n\n" + request.question().trim()));

        // LinkedHashSet: the rider sees which data the answer drew on, in call
        // order, without a tool named twice showing up twice.
        LinkedHashSet<String> toolsUsed = new LinkedHashSet<>();
        JourneySearchResponse routes = null;
        List<TripResponse> trips = List.of();
        String reply = null;

        try {
            for (int round = 0; round < properties.maxToolIterations(); round++) {
                GenerateContentResponse response = client.models.generateContent(
                        properties.model(), messages, generationConfig(true));
                Content modelContent = contentOf(response);
                if (modelContent != null) {
                    // Preserve the complete model turn, including Gemini thought
                    // signatures attached to function calls.
                    messages.add(modelContent);
                }

                List<FunctionCall> calls = response.functionCalls();
                calls = calls == null ? List.of() : calls;

                if (calls.isEmpty()) {
                    reply = response.text();
                    break;
                }

                List<Part> results = new ArrayList<>(calls.size());
                for (FunctionCall call : calls) {
                    String name = call.name().orElse("");
                    toolsUsed.add(name);
                    AssistantToolbox.Outcome outcome = toolbox.invoke(
                            name, call.args().orElse(Map.of()), user, request.context());
                    if (outcome.routes() != null) {
                        routes = outcome.routes();
                    }
                    if (!outcome.trips().isEmpty()) {
                        trips = outcome.trips();
                    }
                    FunctionResponse.Builder functionResponse = FunctionResponse.builder()
                            .name(name)
                            .response(resultPayload(outcome));
                    call.id().ifPresent(functionResponse::id);
                    results.add(Part.builder().functionResponse(functionResponse.build()).build());
                }

                // Every function call must be answered in a single user turn, or
                // the API rejects the follow-up.
                messages.add(Content.builder()
                        .role("user")
                        .parts(results)
                        .build());
            }

            // Out of tool budget with the model still calling tools. Rather than
            // return nothing, ask once more with no tools attached so it has to
            // answer from what it already has.
            if (reply == null) {
                messages.add(userMessage(
                        "You have used your lookup budget for this question. Answer now using only "
                                + "what the lookups already returned, and say plainly which part of "
                                + "the question you could not answer."));
                reply = client.models.generateContent(
                        properties.model(), messages, generationConfig(false)).text();
            }
        } catch (ApiException | GenAiIOException | IllegalArgumentException exception) {
            log.warn("Ask FareFlow Gemini call failed", exception);
            throw new AssistantUnavailableException(
                    "Ask FareFlow could not reach its language model just now. Try again in a moment.",
                    exception);
        }

        if (reply == null || reply.isBlank()) {
            throw new AssistantUnavailableException(
                    "Ask FareFlow did not produce an answer for that. Try rephrasing the question.");
        }

        return new AssistantResponse(
                reply.trim(), List.copyOf(toolsUsed), routes, trips, followUps(routes, toolsUsed));
    }

    // -------------------------------------------------------------- plumbing

    private GenerateContentConfig generationConfig(boolean includeTools) {
        GenerateContentConfig.Builder config = GenerateContentConfig.builder()
                .systemInstruction(Content.builder().parts(Part.fromText(SYSTEM_PROMPT)).build())
                .maxOutputTokens((int) Math.min(Integer.MAX_VALUE, properties.maxTokens()))
                // FareFlow executes calls itself because every lookup must receive
                // the authenticated User object, which is never model-controlled.
                .automaticFunctionCalling(AutomaticFunctionCallingConfig.builder()
                        .disable(true)
                        .build());
        if (includeTools) {
            config.tools(Tool.builder().functionDeclarations(toolbox.tools()).build());
        }
        return config.build();
    }

    /**
     * Facts about right now that the model may state, and only these.
     *
     * <p>Today's date is here because "tomorrow" and "the rest of the week" are
     * unanswerable without it, and a model guessing the date is the same class of
     * error as a model guessing a fare.
     */
    private String context(User user, AssistantPageContext pageContext) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), user.zoneId());
        StringBuilder value = new StringBuilder("""
                [Context — you may state these, everything else must come from a lookup]
                Today is %s, a %s. The rider's timezone is %s. Their display name is %s.
                """.formatted(
                DATE.format(today),
                today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                user.zoneId().getId(),
                user.getName()));
        if (pageContext != null) {
            value.append("The rider is currently on ")
                    .append(safe(pageContext.pageName(), "this FareFlow page"))
                    .append(" (").append(safe(pageContext.pagePath(), "unknown path")).append(").\n");
            AssistantPageContext.ActiveRouteSearch search = pageContext.activeRouteSearch();
            if (search != null && search.isSearchable()) {
                value.append("The visible route search is from ").append(search.origin())
                        .append(" to ").append(search.destination())
                        .append(" with profile ").append(safe(search.profile(), "BALANCED"));
                if (search.selectedJourneyId() != null && !search.selectedJourneyId().isBlank()) {
                    value.append("; selected journey id ").append(search.selectedJourneyId());
                }
                value.append(". These are navigation context only; use a route lookup for every route fact.\n");
            }
        }
        return value.toString();
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.replaceAll("[\\r\\n]", " ").trim();
    }

    private List<Content> replayHistory(List<AssistantTurn> history) {
        List<AssistantTurn> usable = history.stream().filter(AssistantTurn::isUsable).toList();
        // Keep the tail: the most recent turns are the ones a follow-up refers to.
        List<AssistantTurn> trimmed = usable.size() > properties.maxHistoryTurns()
                ? usable.subList(usable.size() - properties.maxHistoryTurns(), usable.size())
                : usable;

        List<Content> messages = new ArrayList<>(trimmed.size());
        for (AssistantTurn turn : trimmed) {
            messages.add(Content.builder()
                    .role(turn.isUser() ? "user" : "model")
                    .parts(Part.fromText(turn.content()))
                    .build());
        }
        // The API requires the first message to be from the user; a thread that
        // somehow starts with an assistant turn gets that turn dropped.
        while (!messages.isEmpty() && !"user".equals(messages.get(0).role().orElse(null))) {
            messages.remove(0);
        }
        return messages;
    }

    private static Content userMessage(String text) {
        return Content.builder().role("user").parts(Part.fromText(text)).build();
    }

    private static Content contentOf(GenerateContentResponse response) {
        return response.candidates()
                .filter(candidates -> !candidates.isEmpty())
                .flatMap(candidates -> candidates.getFirst().content())
                .orElse(null);
    }

    private Map<String, Object> resultPayload(AssistantToolbox.Outcome outcome) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (outcome.isError()) {
            payload.put("error", outcome.json());
            return payload;
        }
        try {
            Object output = objectMapper.readValue(outcome.json(), Object.class);
            payload.put("output", output);
        } catch (JsonProcessingException exception) {
            // Toolbox outcomes are normally JSON. Keeping a string fallback means
            // an unusual serializer result still reaches the model as data.
            payload.put("output", outcome.json());
        }
        return payload;
    }

    /**
     * Next questions worth asking, derived from what just happened rather than
     * generated — so a chip never offers a conversation the data cannot support.
     */
    private static List<String> followUps(JourneySearchResponse routes, LinkedHashSet<String> toolsUsed) {
        List<String> followUps = new ArrayList<>();
        if (routes != null && routes.options().size() > 1) {
            followUps.add("Is there a faster option?");
            followUps.add("Find me a route with less walking");
            followUps.add("Why did you recommend that one?");
        }
        if (toolsUsed.contains("get_budget_status") || toolsUsed.contains("get_weekly_insights")) {
            followUps.add("What changed that spending?");
            followUps.add("How does that compare to last month?");
        }
        if (followUps.isEmpty()) {
            followUps.add("How much have I spent on transit this week?");
            followUps.add("Would a transit pass save me money?");
        }
        return followUps.stream().limit(3).toList();
    }
}
