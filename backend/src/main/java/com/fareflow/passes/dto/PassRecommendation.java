package com.fareflow.passes.dto;

import java.util.List;

/**
 * Whether a pass would beat paying per ride.
 *
 * <p>{@code recommendedPassCode} is null when pay-per-ride wins, or when there is
 * not enough history to say. The distinction is carried in {@code confidence} so
 * the UI never presents a guess as advice.
 *
 * @param assumptions stated plainly, because a savings figure is only meaningful
 *                    alongside what it assumed
 */
public record PassRecommendation(
        boolean hasEnoughHistory,
        int weeksOfHistory,
        long observedWeeklySpendCents,
        long projectedMonthlySpendCents,
        String recommendedPassCode,
        String recommendedPassName,
        Long recommendedPassPriceCents,
        Long monthlySavingsCents,
        String verdict,
        String confidence,
        List<PassOption> options,
        List<String> assumptions
) {

    /**
     * @param monthlySavingsCents negative when the pass costs more than paying per ride
     */
    public record PassOption(
            String code,
            String name,
            String agency,
            long priceCents,
            long monthlyCostCents,
            long monthlySavingsCents,
            boolean worthwhile
    ) {
    }

    public static PassRecommendation insufficientHistory(int weeks, long weeklySpendCents) {
        return new PassRecommendation(
                false, weeks, weeklySpendCents, 0,
                null, null, null, null,
                "Not enough travel history yet to compare passes.",
                "INSUFFICIENT_DATA",
                List.of(),
                List.of("A pass recommendation needs at least one full week of completed trips."));
    }
}
