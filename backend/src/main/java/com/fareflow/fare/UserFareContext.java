package com.fareflow.fare;

/**
 * What the fare engine knows about the rider.
 *
 * <p>Caps and passes are properties of a person's week, not of a journey, so they
 * arrive separately from the itinerary being priced.
 *
 * @param spentThisWeekCents already charged this week, used to apply weekly caps
 * @param spentTodayCents    already charged today, used to apply daily caps
 * @param activePasses       agency codes the rider holds a valid pass for
 */
public record UserFareContext(
        long spentThisWeekCents,
        long spentTodayCents,
        java.util.Set<String> activePasses
) {

    public UserFareContext {
        activePasses = activePasses == null ? java.util.Set.of() : java.util.Set.copyOf(activePasses);
    }

    /** A rider with no history and no passes — the default for an anonymous quote. */
    public static UserFareContext anonymous() {
        return new UserFareContext(0, 0, java.util.Set.of());
    }

    public boolean holdsPassFor(String agency) {
        return activePasses.contains(agency);
    }
}
