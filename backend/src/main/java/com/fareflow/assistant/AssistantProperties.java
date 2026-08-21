package com.fareflow.assistant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Ask FareFlow assistant.
 *
 * <p>The assistant is optional in exactly the way the TomTom map is: with no key
 * configured the rest of FareFlow works unchanged and the panel says so, rather
 * than the app failing to start or the endpoint returning 500s.
 *
 * @param apiKey            Gemini API key. Blank disables the assistant
 * @param model             the Gemini model id; pinned in config so an upgrade is
 *                          a deploy, not a code change
 * @param maxToolIterations ceiling on tool-call rounds per question. A bound is
 *                          required: without one a confused model can loop until
 *                          the request times out and the bill is spent
 * @param maxHistoryTurns   how much prior conversation to replay. The API is
 *                          stateless, so this is the entire memory of a thread
 */
@ConfigurationProperties(prefix = "fareflow.assistant")
public record AssistantProperties(
        boolean enabled,
        String apiKey,
        String model,
        long maxTokens,
        int maxToolIterations,
        int maxHistoryTurns
) {

    public AssistantProperties {
        model = (model == null || model.isBlank()) ? "gemini-3.7-flash" : model;
        maxTokens = maxTokens <= 0 ? 4096 : maxTokens;
        maxToolIterations = maxToolIterations <= 0 ? 6 : maxToolIterations;
        maxHistoryTurns = maxHistoryTurns <= 0 ? 12 : maxHistoryTurns;
    }

    /** Configured *and* keyed. Either one missing means the panel shows a notice. */
    public boolean isUsable() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
