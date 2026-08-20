package com.fareflow.budget;

import com.fareflow.common.WeekWindow;

/**
 * Derived weekly figures. Nothing here is stored — every value is computed from
 * the ledger and the trips table on request.
 *
 * @param weeklyBudgetCents        null when the rider has not set a budget. Not
 *                                 zero — a missing budget and a zero budget lead
 *                                 to different answers everywhere downstream
 * @param spentCents               negation of the ledger net. May be negative in a
 *                                 week where refunds exceed charges; reported
 *                                 truthfully rather than clamped to zero
 * @param remainingCents           null when there is no budget to remain from
 * @param savedVersusFastestCents  null when no trip in the window has a baseline,
 *                                 meaning "not computable" rather than "zero"
 */
public record WeeklySummary(
        WeekWindow week,
        Long weeklyBudgetCents,
        long spentCents,
        Long remainingCents,
        Double budgetUtilization,
        long tripCount,
        Long savedVersusFastestCents
) {

    public boolean hasBudget() {
        return weeklyBudgetCents != null;
    }
}
