package com.fareflow.user.dto;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * The budget is the only mutable field in Phase 1, so it gets its own narrow
 * endpoint rather than a whole-object PUT that could overwrite the email by accident.
 */
public record UpdateBudgetRequest(

        @PositiveOrZero(message = "Weekly budget must be zero or greater")
        long weeklyBudgetCents
) {
}
