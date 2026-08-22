package com.fareflow.insights.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Transportation spending and travel over a chosen window, bucketed for charting.
 *
 * <p>Every figure is summed from completed trips and the ledger. Nothing is
 * extrapolated, smoothed, or back-filled: a bucket with no trips reports zero
 * spend and a null average, because "you spent nothing that day" and "there is no
 * average fare to report" are different facts.
 *
 * <p>The series never starts before the rider's first trip. Charting twelve empty
 * months for someone who joined last week would imply a year of $0 spending that
 * never happened.
 *
 * @param hasData          false when the window holds no completed trips at all;
 *                         the client shows an empty state rather than a flat line
 * @param firstTripDate    the rider's first completed trip ever, null if none
 * @param rangesWithData   which of the four ranges are worth offering this rider,
 *                         so the UI can disable the ones that would render empty
 * @param weeklyBudgetCents the rider's current budget, for a reference line on
 *                          weekly charts. Null when unset — never zero
 * @param comparison       the same measures over the immediately preceding window
 *                         of equal length, or null when there is no such history
 */
public record SpendingHistoryResponse(
        String range,
        String rangeName,
        String granularity,
        LocalDate startDate,
        LocalDate endDate,
        boolean hasData,
        LocalDate firstTripDate,
        List<String> rangesWithData,
        Long weeklyBudgetCents,
        Totals totals,
        Comparison comparison,
        List<Bucket> buckets,
        List<OperatorSlice> byOperator,
        List<ModeSlice> byMode,
        List<RouteSlice> mostUsedRoutes
) {

    /**
     * One bar.
     *
     * @param averageFareCents      null when the bucket holds no trips
     * @param savedCents            versus taking the fastest option each time;
     *                              null when no trip in the bucket recorded a
     *                              comparable baseline
     * @param cumulativeSpentCents  running total from the start of the series,
     *                              for the savings-over-time and burn-down views
     */
    public record Bucket(
            LocalDate date,
            String label,
            long spentCents,
            long tripCount,
            Long averageFareCents,
            Long averageDurationMinutes,
            Long savedCents,
            long cumulativeSpentCents
    ) {
    }

    public record Totals(
            long spentCents,
            long tripCount,
            Long averageFareCents,
            Long averageDurationMinutes,
            Long savedCents,
            long totalMinutes,
            Long totalDistanceMetres,
            Long costPerMileCents,
            long usagePricedTripCount
    ) {
    }

    /**
     * The previous window of the same length — the raw material for "what changed".
     *
     * @param spentChangePercent null when the previous window had no spending;
     *                           a percentage change from zero is not a number
     */
    public record Comparison(
            LocalDate startDate,
            LocalDate endDate,
            long spentCents,
            long tripCount,
            Long averageFareCents,
            long spentChangeCents,
            Double spentChangePercent
    ) {
    }

    public record OperatorSlice(
            String provider,
            String providerName,
            long tripCount,
            long spentCents,
            long averageFareCents,
            double shareOfSpend
    ) {
    }

    public record ModeSlice(
            String mode,
            String modeName,
            long tripCount,
            long spentCents,
            double shareOfSpend
    ) {
    }

    public record RouteSlice(
            String origin,
            String destination,
            String provider,
            long tripCount,
            long totalFareCents,
            long averageFareCents
    ) {
    }
}
