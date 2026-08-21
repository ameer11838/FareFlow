package com.fareflow.assistant;

/**
 * The assistant cannot answer right now — no API key configured, or the upstream
 * call failed. Distinct from a bad request so the client can show a configuration
 * notice rather than blaming the rider's question.
 */
public class AssistantUnavailableException extends RuntimeException {

    public AssistantUnavailableException(String message) {
        super(message);
    }

    public AssistantUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
