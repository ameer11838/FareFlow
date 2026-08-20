package com.fareflow.insights;

import com.fareflow.budget.BudgetService;
import com.fareflow.budget.WeeklySummary;
import com.fareflow.common.Money;
import com.fareflow.insights.dto.InsightsResponse;
import com.fareflow.passes.PassOptimizationService;
import com.fareflow.passes.TransitPass;
import com.fareflow.profile.CommuteFrequency;
import com.fareflow.profile.PassPreference;
import com.fareflow.profile.TravelProfileService;
import com.fareflow.profile.UserTravelProfile;
import com.fareflow.route.TransitProvider;
import com.fareflow.trip.TripRepository;
import com.fareflow.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Derived transportation-spending intelligence.
 *
 * <p>Every figure comes from real trip and ledger rows. Where something cannot be
 * derived — no trips yet, no comparable baseline — the field is null and the UI
 * shows nothing rather than a fabricated number. That rule is the whole reason
 * this service exists instead of the frontend computing its own statistics.
 *
 * <p>The personalized block follows the same rule with one addition: a figure that
 * rests on a stated assumption ships with the assumption attached. "Projected to
 * spend $46" is a guess; "projected to spend $46, assuming your usual 3 commuting
 * days at your average $3.83 fare" is arithmetic the rider can check.
 */
@Service
@Transactional(readOnly = true)
public class InsightsService {

    /** A commuting day is a there-and-back pair. Stated, not assumed silently. */
    private static final int TRIPS_PER_COMMUTING_DAY = 2;

    private final BudgetService budgetService;
    private final TripRepository tripRepository;
    private final TravelProfileService travelProfileService;
    private final List<TransitPass> availablePasses;

    public InsightsService(BudgetService budgetService,
                           TripRepository tripRepository,
                           TravelProfileService travelProfileService,
                           List<TransitPass> availablePasses) {
        this.budgetService = budgetService;
        this.tripRepository = tripRepository;
        this.travelProfileService = travelProfileService;
        this.availablePasses = List.copyOf(availablePasses);
    }

    public InsightsResponse forCurrentWeek(User user) {
        WeeklySummary summary = budgetService.currentWeek(user.getId());
        var week = summary.week();

        List<TripRepository.ProviderUsage> usage =
                tripRepository.findProviderUsageBetween(user.getId(), week.start(), week.end());

        long completedTrips = summary.tripCount();

        // Averages are only meaningful with at least one completed trip.
        Long averageFareCents = null;
        Long averageDurationMinutes = null;
        if (completedTrips > 0) {
            long totalFare = usage.stream().mapToLong(TripRepository.ProviderUsage::getTotalFareCents).sum();
            averageFareCents = Math.round((double) totalFare / completedTrips);
            long totalMinutes = tripRepository.sumDurationBetween(user.getId(), week.start(), week.end());
            averageDurationMinutes = Math.round((double) totalMinutes / completedTrips);
        }

        List<InsightsResponse.ProviderBreakdown> breakdown = usage.stream()
                .map(row -> new InsightsResponse.ProviderBreakdown(
                        row.getProvider(),
                        displayNameOf(row.getProvider()),
                        row.getTripCount(),
                        row.getTotalFareCents(),
                        Math.round(row.getAverageFareCents()),
                        Math.round(row.getAverageDurationMinutes())))
                .toList();

        // "Cheapest/fastest provider used" only makes sense with something to compare.
        String cheapestProvider = usage.size() >= 2
                ? usage.stream().min(java.util.Comparator.comparingLong(TripRepository.ProviderUsage::getMinFareCents))
                        .map(TripRepository.ProviderUsage::getProvider).orElse(null)
                : null;
        String fastestProvider = usage.size() >= 2
                ? usage.stream().min(java.util.Comparator.comparingInt(TripRepository.ProviderUsage::getMinDurationMinutes))
                        .map(TripRepository.ProviderUsage::getProvider).orElse(null)
                : null;

        Long minutesTraded = tripRepository.sumMinutesTradedBetween(user.getId(), week.start(), week.end());

        return new InsightsResponse(
                summary.spentCents(),
                summary.weeklyBudgetCents(),
                summary.remainingCents(),
                summary.budgetUtilization(),
                completedTrips,
                summary.savedVersusFastestCents(),
                averageFareCents,
                averageDurationMinutes,
                cheapestProvider,
                cheapestProvider == null ? null : displayNameOf(cheapestProvider),
                fastestProvider,
                fastestProvider == null ? null : displayNameOf(fastestProvider),
                minutesTraded,
                projectedMonthlyCents(summary.spentCents()),
                breakdown,
                personalize(user, summary, usage, averageFareCents));
    }

    /**
     * The part of Insights that exists only because the rider answered onboarding.
     *
     * <p>Returns null when there is no profile at all — an empty personalization
     * block would be a place for the UI to render a row of dashes for questions
     * nobody was ever asked.
     */
    private InsightsResponse.Personalization personalize(User user,
                                                         WeeklySummary summary,
                                                         List<TripRepository.ProviderUsage> usage,
                                                         Long averageFareCents) {

        UserTravelProfile profile = travelProfileService.find(user.getId()).orElse(null);
        if (profile == null) {
            return null;
        }

        CommuteFrequency frequency = profile.getWeeklyCommuteFrequency();
        Integer daysPerWeek = frequency == null ? null : frequency.estimatedDaysPerWeek();
        List<String> notes = new ArrayList<>();

        if (frequency != null) {
            notes.add("You told FareFlow you commute %s."
                    .formatted(frequency.displayName().toLowerCase(java.util.Locale.ROOT)));
        }

        // ---- Projected spend for the week ----
        //
        // Rate x price, both of them the rider's own numbers: their stated commute
        // frequency and their observed average fare. Floored at what they have
        // already spent, because a projection that comes in under the actual is not
        // a projection, it is a contradiction.
        Long projected = null;
        if (daysPerWeek != null && averageFareCents != null) {
            long plannedSpend = averageFareCents * daysPerWeek * TRIPS_PER_COMMUTING_DAY;
            projected = Math.max(summary.spentCents(), plannedSpend);
            notes.add("At %s a trip across about %d commuting days, this week is tracking toward %s."
                    .formatted(Money.format(averageFareCents), daysPerWeek, Money.format(projected)));
        }

        // ---- Budget buffer ----
        Long buffer = null;
        if (projected != null && summary.hasBudget()) {
            buffer = summary.weeklyBudgetCents() - projected;
            notes.add(buffer >= 0
                    ? "Your %s weekly budget leaves about %s of buffer."
                            .formatted(Money.format(summary.weeklyBudgetCents()), Money.format(buffer))
                    : "That is about %s more than your %s weekly budget."
                            .formatted(Money.format(-buffer), Money.format(summary.weeklyBudgetCents())));
        } else if (!summary.hasBudget()) {
            notes.add("Set a weekly budget and FareFlow can tell you whether this pace fits it.");
        }

        // ---- Pass suggestion ----
        PassSuggestion suggestion = suggestPass(profile, usage, daysPerWeek);
        if (suggestion != null) {
            notes.add("Based on your commute frequency, a %s could save about %s a week."
                    .formatted(suggestion.name(), Money.format(suggestion.weeklySavingsCents())));
        }

        return new InsightsResponse.Personalization(
                frequency == null ? null : frequency.name(),
                frequency == null ? null : frequency.displayName(),
                daysPerWeek,
                profile.getTypicalOrigin().name(),
                profile.getTypicalDestination().name(),
                projected,
                buffer,
                suggestion == null ? null : suggestion.code(),
                suggestion == null ? null : suggestion.name(),
                suggestion == null ? null : suggestion.weeklySavingsCents(),
                List.copyOf(notes));
    }

    private record PassSuggestion(String code, String name, long weeklySavingsCents) {
    }

    /**
     * The best pass that beats paying per ride at the rider's stated commute rate.
     *
     * <p>Only for agencies they have actually travelled with this week, and only
     * when a pass genuinely wins — a recommender that always finds something to buy
     * is an advertisement. Skipped entirely for riders who already hold a pass:
     * they have made this decision, and telling them again is noise.
     */
    private PassSuggestion suggestPass(UserTravelProfile profile,
                                       List<TripRepository.ProviderUsage> usage,
                                       Integer daysPerWeek) {
        PassPreference preference = profile.getPassPreference();
        if (daysPerWeek == null || usage.isEmpty()
                || (preference != null && !preference.openToPassAdvice())) {
            return null;
        }

        PassSuggestion best = null;
        for (TransitPass pass : availablePasses) {
            // The rider's own average fare on the agency this pass covers.
            var agencyRows = usage.stream()
                    .filter(row -> PassOptimizationService.agencyOf(row.getProvider())
                            .equals(pass.coversAgency()))
                    .toList();
            if (agencyRows.isEmpty()) {
                continue;
            }

            long agencyTrips = agencyRows.stream()
                    .mapToLong(TripRepository.ProviderUsage::getTripCount).sum();
            long agencySpend = agencyRows.stream()
                    .mapToLong(TripRepository.ProviderUsage::getTotalFareCents).sum();
            if (agencyTrips == 0) {
                continue;
            }

            long averageFare = Math.round((double) agencySpend / agencyTrips);
            long weeklyPayPerRide = averageFare * daysPerWeek * TRIPS_PER_COMMUTING_DAY;
            long savings = weeklyPayPerRide - Math.round(pass.weeklyEquivalentCents());

            if (savings > 0 && (best == null || savings > best.weeklySavingsCents())) {
                best = new PassSuggestion(pass.code(), pass.name(), savings);
            }
        }
        return best;
    }

    /**
     * A straight-line projection of this week's spend across a month.
     *
     * <p>Null until there is a week with spending, because projecting from zero
     * would present $0.00 as a forecast rather than as "not enough data".
     * Explicitly a naive projection — one week is not a trend, and the UI says so.
     */
    private static Long projectedMonthlyCents(long spentThisWeekCents) {
        if (spentThisWeekCents <= 0) {
            return null;
        }
        return Math.round(spentThisWeekCents * (52.0 / 12.0));
    }

    private static String displayNameOf(String provider) {
        try {
            return TransitProvider.valueOf(provider).displayName();
        } catch (IllegalArgumentException exception) {
            return provider;
        }
    }
}
