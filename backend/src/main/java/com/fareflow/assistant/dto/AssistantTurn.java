package com.fareflow.assistant.dto;

/**
 * One prior turn, replayed by the client.
 *
 * <p>The Messages API is stateless and FareFlow stores no conversation, so the
 * thread lives in the browser tab and is sent back with each question. Nothing
 * about a rider's chat is persisted server-side.
 *
 * @param role "user" or "assistant"; anything else is dropped
 */
public record AssistantTurn(String role, String content) {

    public boolean isUser() {
        return "user".equalsIgnoreCase(role);
    }

    public boolean isAssistant() {
        return "assistant".equalsIgnoreCase(role);
    }

    public boolean isUsable() {
        return content != null && !content.isBlank() && (isUser() || isAssistant());
    }
}
