package com.fareflow.wallet.dto;

import com.fareflow.ledger.dto.LedgerEntryResponse;

import java.util.List;

/**
 * The wallet view.
 *
 * <p><strong>There is no wallet table.</strong> Every figure here is derived from
 * the ledger, which stays the single source of financial truth. Introducing a
 * stored balance would create a second truth that could drift from the entries
 * that justify it — exactly the mistake the ledger design exists to prevent.
 *
 * @param availableBalanceCents remaining weekly transportation budget. Named
 *                              "available" for the UI, but it is a budget figure,
 *                              not custody of real money. Null when the rider has
 *                              set no budget — there is no balance to report, and
 *                              rendering $0.00 would read as "you are out of money"
 * @param paymentMethods        what can pay for a fare today, plus what is planned
 */
public record WalletResponse(
        Long availableBalanceCents,
        long spentThisWeekCents,
        Long weeklyBudgetCents,
        Double budgetUtilization,
        List<PaymentMethod> paymentMethods,
        List<LedgerEntryResponse> recentActivity
) {

    /**
     * @param status AVAILABLE means it can pay for a fare now; COMING_SOON is
     *               displayed but not selectable. No real rails are implemented.
     */
    public record PaymentMethod(
            String id,
            String name,
            String description,
            String status
    ) {
        public static final String AVAILABLE = "AVAILABLE";
        public static final String COMING_SOON = "COMING_SOON";
    }
}
