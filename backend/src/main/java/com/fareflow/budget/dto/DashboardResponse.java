package com.fareflow.budget.dto;

import com.fareflow.budget.WeeklySummary;
import com.fareflow.trip.dto.TripResponse;
import com.fareflow.user.dto.UserResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the dashboard needs, in one call.
 *
 * <p>One endpoint rather than four: separate calls could interleave with a trip
 * being created and render figures that do not reconcile — spend from before,
 * trip count from after. A single query boundary gives one consistent snapshot.
 *
 * @param savedVersusFastestCents null when not computable. The client renders a
 *        dash and an explanatory caption, never "$0.00", which would read as
 *        "FareFlow saved you nothing"
 */
public record DashboardResponse(
        UserResponse user,
        Week week,
        long spentCents,
        Long remainingCents,
        Double budgetUtilization,
        long tripCount,
        Long savedVersusFastestCents,
        List<TripResponse> recentTrips
) {

    public record Week(LocalDate startDate, Instant startsAt, Instant endsAt, String timezone) {
    }

    public static DashboardResponse of(UserResponse user, WeeklySummary summary,
                                       List<TripResponse> recentTrips) {
        return new DashboardResponse(
                user,
                new Week(summary.week().weekStartDate(),
                        summary.week().start(),
                        summary.week().end(),
                        summary.week().zone().getId()),
                summary.spentCents(),
                summary.remainingCents(),
                summary.budgetUtilization(),
                summary.tripCount(),
                summary.savedVersusFastestCents(),
                recentTrips);
    }
}
