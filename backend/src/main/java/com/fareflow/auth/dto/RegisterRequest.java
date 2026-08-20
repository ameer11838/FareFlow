package com.fareflow.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name must be 120 characters or fewer")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 200, message = "Email must be 200 characters or fewer")
        String email,

        // Length is the constraint that actually matters; composition rules push
        // people toward predictable substitutions.
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 200, message = "Password must be at least 8 characters")
        String password,

        /*
         * Optional, and normally absent. The registration form does not ask for a
         * budget -- onboarding does. The field stays on the contract so an API
         * client that already knows the number can supply it, and so the value can
         * be null, which means "not set yet" rather than "$0.00".
         */
        @PositiveOrZero(message = "Weekly budget must be zero or greater")
        Long weeklyBudgetCents,

        String timezone
) {

    public String timezoneOrDefault() {
        return timezone == null || timezone.isBlank() ? "America/New_York" : timezone;
    }
}
