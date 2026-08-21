package com.fareflow.assistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A question for Ask FareFlow, plus the thread it belongs to.
 *
 * @param history prior turns, oldest first. Trimmed server-side to the configured
 *                window; a client cannot make a request arbitrarily large by
 *                replaying a thousand turns
 */
public record AskRequest(
        @NotBlank(message = "question is required")
        @Size(max = 1000, message = "question must be 1000 characters or fewer")
        String question,

        List<AssistantTurn> history,

        @Valid AssistantPageContext context
) {

    public List<AssistantTurn> historyOrEmpty() {
        return history == null ? List.of() : history;
    }
}
