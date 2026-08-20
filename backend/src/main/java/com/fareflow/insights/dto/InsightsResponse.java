package com.fareflow.insights.dto;

import java.util.List;

/**
 * Weekly transportation intelligence.
 *
 * <p>Every nullable field means "not derivable from the data yet" rather than
 * zero. The client renders a dash for those, never a fabricated figure.
 *
 * @param weeklyBudgetCents       null when the rider has set no budget. The client
 *                                prompts for one instead of reporting $0.00
 * @param remainingCents          null for the same reason: there is nothing to
 *                                remain from
 * @param projectedMonthlyCents   naive straight-line projection from one week; null
 *                                until there is spending to project from
 * @param minutesTradedForSavings extra minutes spent versus always taking the
 *                                fastest route; the time cost of the money saved
 * @param personalization         figures that exist only because the rider told
 *                                FareFlow about their travel during onboarding.
 *                                Null when there is no profile to personalise from
 */
public record InsightsResponse(
        long spentCents,
        Long weeklyBudgetCents,
        Long remainingCents,
        Double budgetUtilization,
        long tripCount,
        Long savedVersusFastestCents,
        Long averageFareCents,
        Long averageDurationMinutes,
        String cheapestProvider,
        String cheapestProviderName,
        String fastestProvider,
        String fastestProviderName,
        Long minutesTradedForSavings,
        Long projectedMonthlyCents,
        List<ProviderBreakdown> spendingByProvider,
        Personalization personalization
) {

    public record ProviderBreakdown(
            String provider,
            String providerName,
            long tripCount,
            long totalFareCents,
            long averageFareCents,
            long averageDurationMinutes
    ) {
    }

    /**
     * What the onboarding answers actually buy the rider.
     *
     * <p>Nothing here is a guess dressed up as a number. Each field is null unless
     * every input it needs exists, and {@code notes} states the assumption behind
     * any figure that rests on one — because "projected to spend $46" is only
     * honest next to "assuming your usual 4 commuting days".
     *
     * @param projectedWeeklySpendCents what this week looks like it will cost at
     *                                  the rider's stated commute rate, using
     *                                  their own average fare. Never lower than
     *                                  what they have already spent
     * @param budgetBufferCents         budget minus that projection. Negative is a
     *                                  real answer and is reported as one
     * @param suggestedPassSavingsCents weekly saving from the best pass that beats
     *                                  paying per ride at this commute rate, or
     *                                  null when no pass does
     */
    public record Personalization(
            String commuteFrequency,
            String commuteFrequencyName,
            Integer commuteDaysPerWeek,
            String typicalOriginName,
            String typicalDestinationName,
            Long projectedWeeklySpendCents,
            Long budgetBufferCents,
            String suggestedPassCode,
            String suggestedPassName,
            Long suggestedPassSavingsCents,
            List<String> notes
    ) {
    }
}
