package com.fareflow.assistant.dto;

import java.util.List;

/**
 * Whether the assistant can answer at all, and what to offer if it can.
 *
 * <p>The client asks first so it can render a configuration notice instead of a
 * chat box that fails on the first message.
 *
 * @param unavailableReason plain-language explanation when {@code available} is
 *                          false; null otherwise
 * @param starters          opening questions, tailored to what this rider has
 *                          actually told FareFlow
 */
public record AssistantConfigResponse(
        boolean available,
        String unavailableReason,
        List<String> starters
) {
}
