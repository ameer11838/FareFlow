package com.fareflow.assistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Non-authoritative UI context attached to an assistant turn.
 *
 * <p>Place names and route ids help the assistant understand what the rider is
 * looking at, but no transit or financial fact is accepted here. When a route
 * needs explaining or changing the toolbox reruns the deterministic planner.
 */
public record AssistantPageContext(
        @Size(max = 120) String pagePath,
        @Size(max = 80) String pageName,
        @Valid ActiveRouteSearch activeRouteSearch
) {

    public record ActiveRouteSearch(
            @Size(max = 250) String origin,
            @Size(max = 250) String destination,
            @Size(max = 40) String profile,
            @Size(max = 160) String selectedJourneyId
    ) {
        public boolean isSearchable() {
            return origin != null && !origin.isBlank()
                    && destination != null && !destination.isBlank();
        }
    }
}
