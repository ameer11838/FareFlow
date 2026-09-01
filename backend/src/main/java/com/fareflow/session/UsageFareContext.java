package com.fareflow.session;

import com.fareflow.profile.FareCategory;

/** Values snapshotted when a trip starts so its pricing cannot drift mid-ride. */
public record UsageFareContext(
        FareCategory fareCategory,
        long spentTodayCents,
        long spentThisWeekCents
) {
    public UsageFareContext {
        fareCategory = fareCategory == null ? FareCategory.REGULAR : fareCategory;
        if (spentTodayCents < 0 || spentThisWeekCents < 0) {
            throw new IllegalArgumentException("Fare-cap spending cannot be negative");
        }
    }

    public static UsageFareContext regular() {
        return new UsageFareContext(FareCategory.REGULAR, 0, 0);
    }
}
