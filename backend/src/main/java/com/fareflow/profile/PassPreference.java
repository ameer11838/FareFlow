package com.fareflow.profile;

import java.util.Arrays;
import java.util.Optional;

/**
 * How the rider currently pays.
 *
 * <p>Deliberately not a payment instrument. FareFlow asks how someone buys transit,
 * never for a card, an account, or anything a bank would recognise.
 */
public enum PassPreference {

    PAY_PER_RIDE("Pay per ride"),
    WEEKLY_PASS("Weekly pass"),
    MONTHLY_PASS("Monthly pass"),
    NOT_SURE("Not sure");

    private final String displayName;

    PassPreference(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Whether pass advice is worth showing: someone already on a pass has decided. */
    public boolean openToPassAdvice() {
        return this == PAY_PER_RIDE || this == NOT_SURE;
    }

    public static Optional<PassPreference> parse(String raw) {
        return CommuteFrequency.Enums.parse(PassPreference.class, raw);
    }

    public static String validNames() {
        return String.join(", ", Arrays.stream(values()).map(Enum::name).toList());
    }
}
