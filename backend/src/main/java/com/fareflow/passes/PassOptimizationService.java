package com.fareflow.passes;

import com.fareflow.common.Money;
import com.fareflow.passes.dto.PassRecommendation;
import com.fareflow.trip.TripRepository;
import com.fareflow.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decides whether a pass beats paying per ride, from the rider's actual history.
 *
 * <p>Deterministic and conservative. It refuses to answer without at least a full
 * week of trips, states its assumptions, and reports a pass as not worthwhile when
 * that is what the arithmetic says — a recommendation engine that only ever says
 * "buy the pass" is a sales tool, not an advisor.
 *
 * <p>Money is integer cents throughout; the only division is the weeks-of-history
 * average, which is rounded once at the end.
 */
@Service
@Transactional(readOnly = true)
public class PassOptimizationService {

    /** Below this, an average is noise rather than a pattern. */
    private static final int MINIMUM_DAYS_OF_HISTORY = 7;
    private static final double WEEKS_PER_MONTH = 52.0 / 12.0;

    private final TripRepository tripRepository;
    private final List<TransitPass> availablePasses;
    private final Clock clock;

    public PassOptimizationService(TripRepository tripRepository,
                                   List<TransitPass> availablePasses,
                                   Clock clock) {
        this.tripRepository = tripRepository;
        this.availablePasses = List.copyOf(availablePasses);
        this.clock = clock;
    }

    public PassRecommendation recommendFor(User user) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(28, ChronoUnit.DAYS);

        var usage = tripRepository.findProviderUsageBetween(user.getId(), windowStart, now);
        long totalSpentCents = usage.stream()
                .mapToLong(TripRepository.ProviderUsage::getTotalFareCents)
                .sum();
        long totalTrips = usage.stream()
                .mapToLong(TripRepository.ProviderUsage::getTripCount)
                .sum();

        Instant firstTrip = tripRepository.findEarliestTripAt(user.getId(), windowStart);
        long daysOfHistory = firstTrip == null
                ? 0
                : Math.max(1, ChronoUnit.DAYS.between(firstTrip, now));

        if (totalTrips == 0 || daysOfHistory < MINIMUM_DAYS_OF_HISTORY) {
            return PassRecommendation.insufficientHistory(
                    (int) (daysOfHistory / 7),
                    daysOfHistory > 0 ? Math.round(totalSpentCents * 7.0 / daysOfHistory) : 0);
        }

        double weeksObserved = daysOfHistory / 7.0;
        long weeklySpendCents = Math.round(totalSpentCents / weeksObserved);
        long projectedMonthlyCents = Math.round(weeklySpendCents * WEEKS_PER_MONTH);

        // Only consider passes for agencies the rider actually uses -- suggesting a
        // pass for an agency they have never travelled with would be noise.
        List<String> agenciesUsed = usage.stream()
                .map(TripRepository.ProviderUsage::getProvider)
                .map(PassOptimizationService::agencyOf)
                .distinct()
                .toList();

        List<PassRecommendation.PassOption> options = new ArrayList<>();
        for (TransitPass pass : availablePasses) {
            if (!agenciesUsed.contains(pass.coversAgency())) {
                continue;
            }

            // Spend attributable to the agency this pass covers.
            long agencySpendPerWeek = Math.round(usage.stream()
                    .filter(row -> agencyOf(row.getProvider()).equals(pass.coversAgency()))
                    .mapToLong(TripRepository.ProviderUsage::getTotalFareCents)
                    .sum() / weeksObserved);

            long monthlyPayPerRide = Math.round(agencySpendPerWeek * WEEKS_PER_MONTH);
            long monthlyPassCost = Math.round(pass.weeklyEquivalentCents() * WEEKS_PER_MONTH);
            long savings = monthlyPayPerRide - monthlyPassCost;

            options.add(new PassRecommendation.PassOption(
                    pass.code(), pass.name(), pass.coversAgency(),
                    pass.priceCents(), monthlyPassCost, savings, savings > 0));
        }

        options.sort(Comparator.comparingLong(PassRecommendation.PassOption::monthlySavingsCents).reversed());

        PassRecommendation.PassOption best = options.stream()
                .filter(PassRecommendation.PassOption::worthwhile)
                .findFirst()
                .orElse(null);

        String verdict = best != null
                ? "Based on the last %d days, a %s would save about %s a month."
                        .formatted(daysOfHistory, best.name(), Money.format(best.monthlySavingsCents()))
                : "Paying per ride is cheaper than every pass available for the agencies you use.";

        return new PassRecommendation(
                true,
                (int) Math.round(weeksObserved),
                weeklySpendCents,
                projectedMonthlyCents,
                best == null ? null : best.code(),
                best == null ? null : best.name(),
                best == null ? null : best.priceCents(),
                best == null ? null : best.monthlySavingsCents(),
                verdict,
                weeksObserved >= 3 ? "HIGH" : "LOW",
                List.copyOf(options),
                List.of(
                        "Based on %d completed trips over %d days.".formatted(totalTrips, daysOfHistory),
                        "Assumes your recent travel pattern continues unchanged.",
                        "Compares only agencies you have actually travelled with.",
                        "Pass prices are published fares; pay-per-ride uses your recorded fares."));
    }

    /**
     * Maps a trip's provider onto the agency a pass is sold by.
     *
     * <p>Public so the insights service can ask the same question and get the same
     * answer. Two copies of this mapping would eventually disagree, and a pass
     * suggestion that contradicts the pass page is worse than no suggestion.
     */
    public static String agencyOf(String provider) {
        return switch (provider) {
            case "PATH" -> "PATH";
            case "NYC_BUS", "MTA" -> "MTA";
            case "NJ_TRANSIT", "NY_WATERWAY" -> "NJ_TRANSIT";
            default -> provider;
        };
    }
}
