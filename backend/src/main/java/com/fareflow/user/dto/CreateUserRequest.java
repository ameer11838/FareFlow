package com.fareflow.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name must be 120 characters or fewer")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 200, message = "Email must be 200 characters or fewer")
        String email,

        /** Null means no budget set, which is different from a budget of zero. */
        @PositiveOrZero(message = "Weekly budget must be zero or greater")
        Long weeklyBudgetCents,

        String timezone
) {

    public String timezoneOrDefault() {
        return timezone == null || timezone.isBlank() ? "America/New_York" : timezone;
    }
}
