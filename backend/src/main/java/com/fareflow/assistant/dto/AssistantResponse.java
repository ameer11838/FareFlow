package com.fareflow.assistant.dto;

import com.fareflow.discovery.dto.JourneySearchResponse;
import com.fareflow.trip.dto.TripResponse;

import java.util.List;

/**
 * An answer, plus anything the rest of the app should do about it.
 *
 * <p>{@code routes} is the whole reason the assistant is not just a chat box: when
 * the question was about getting somewhere, the planner really ran, and the same
 * priced options the Plan page would have shown come back here — so the map draws
 * them and the rider can take one. Nothing in it was written by the model.
 *
 * @param toolsUsed which data the answer was built from, in call order. Surfaced
 *                  to the rider so an answer about their budget is visibly an
 *                  answer about their budget and not a guess
 * @param routes    null unless the assistant actually planned a journey this turn
 * @param followUps suggested next questions, derived from the rider's own state
 */
public record AssistantResponse(
        String reply,
        List<String> toolsUsed,
        JourneySearchResponse routes,
        List<TripResponse> trips,
        List<String> followUps
) {
}
