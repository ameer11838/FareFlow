package com.fareflow.wallet;

import com.fareflow.budget.BudgetService;
import com.fareflow.budget.WeeklySummary;
import com.fareflow.ledger.LedgerService;
import com.fareflow.ledger.dto.LedgerEntryResponse;
import com.fareflow.payment.PaymentService;
import com.fareflow.payment.dto.PaymentIntentResponse;
import com.fareflow.user.User;
import com.fareflow.wallet.dto.WalletResponse;
import com.fareflow.session.TransitSessionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-only projection of the ledger presented as a wallet.
 *
 * <p>This service writes nothing. Money still moves only through
 * {@code TripService} and {@code LedgerService}, so the wallet cannot become a
 * second way to change a balance.
 */
@Service
@Transactional(readOnly = true)
public class WalletService {

    private final BudgetService budgetService;
    private final LedgerService ledgerService;
    private final PaymentService paymentService;
    private final TransitSessionService transitSessionService;

    public WalletService(BudgetService budgetService,
                         LedgerService ledgerService,
                         PaymentService paymentService,
                         TransitSessionService transitSessionService) {
        this.budgetService = budgetService;
        this.ledgerService = ledgerService;
        this.paymentService = paymentService;
        this.transitSessionService = transitSessionService;
    }

    public WalletResponse forUser(User user) {
        WeeklySummary summary = budgetService.currentWeek(user.getId());

        List<LedgerEntryResponse> recent = ledgerService
                .findForUser(user.getId(), PageRequest.of(0, 8))
                .getContent().stream()
                .map(LedgerEntryResponse::from)
                .toList();
        List<PaymentIntentResponse> recentPayments = paymentService
                .list(user, PageRequest.of(0, 6))
                .getContent();

        return new WalletResponse(
                summary.remainingCents(),
                summary.spentCents(),
                summary.weeklyBudgetCents(),
                summary.budgetUtilization(),
                paymentMethods(),
                recent,
                recentPayments,
                transitSessionService.active(user).orElse(null));
    }

    /**
     * Payment rails. Both use the same intent lifecycle; the card rail is an
     * explicit simulation and never stores or moves real card data.
     */
    private static List<WalletResponse.PaymentMethod> paymentMethods() {
        return List.of(
                new WalletResponse.PaymentMethod(
                        "FAREFLOW_BALANCE",
                        "FareFlow Balance",
                        "Fares are charged against your weekly transportation budget",
                        WalletResponse.PaymentMethod.AVAILABLE),
                new WalletResponse.PaymentMethod(
                        "SIMULATED_CARD",
                        "Simulated card",
                        "Exercises authorization and settlement without moving real money",
                        WalletResponse.PaymentMethod.AVAILABLE));
    }
}
