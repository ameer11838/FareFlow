package com.fareflow.discovery;

/**
 * Raised when a rider selects a journey FareFlow cannot price.
 *
 * <p>The alternative — charging zero — would be the single most dishonest thing
 * this system could do. Mapped to 409 with a machine-readable code so the client
 * can prompt for explicit confirmation.
 */
public class FareConfirmationRequiredException extends RuntimeException {

    public static final String CODE = "FARE_CONFIRMATION_REQUIRED";

    private final String journeySummary;

    public FareConfirmationRequiredException(String journeySummary) {
        super("This journey has no published fare FareFlow can compute. "
                + "Confirm explicitly to record it with no charge.");
        this.journeySummary = journeySummary;
    }

    public String journeySummary() {
        return journeySummary;
    }
}
