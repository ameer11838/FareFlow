package com.fareflow.wallet;

import com.fareflow.budget.BudgetService;
import com.fareflow.budget.WeeklySummary;
import com.fareflow.ledger.LedgerService;
import com.fareflow.ledger.dto.LedgerEntryResponse;
import com.fareflow.user.User;
import com.fareflow.wallet.dto.WalletResponse;
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

    public WalletService(BudgetService budgetService, LedgerService ledgerService) {
        this.budgetService = budgetService;
        this.ledgerService = ledgerService;
    }

    public WalletResponse forUser(User user) {
        WeeklySummary summary = budgetService.currentWeek(user.getId());

        List<LedgerEntryResponse> recent = ledgerService
                .findForUser(user.getId(), PageRequest.of(0, 8))
                .getContent().stream()
                .map(LedgerEntryResponse::from)
                .toList();

        return new WalletResponse(
                summary.remainingCents(),
                summary.spentCents(),
                summary.weeklyBudgetCents(),
                summary.budgetUtilization(),
                paymentMethods(),
                recent);
    }

    /**
     * Payment rails. Only the budget-backed one works; the rest are declared so the
     * checkout flow has a real shape to grow into, and are marked COMING_SOON so
     * the UI cannot let anyone select something that does not exist.
     */
    private static List<WalletResponse.PaymentMethod> paymentMethods() {
        return List.of(
                new WalletResponse.PaymentMethod(
                        "FAREFLOW_BALANCE",
                        "FareFlow Balance",
                        "Fares are charged against your weekly transportation budget",
                        WalletResponse.PaymentMethod.AVAILABLE),
                new WalletResponse.PaymentMethod(
                        "CARD",
                        "Debit or credit card",
                        "Card payments arrive in a later phase",
                        WalletResponse.PaymentMethod.COMING_SOON),
                new WalletResponse.PaymentMethod(
                        "STABLECOIN",
                        "Stablecoin",
                        "Testnet stablecoin settlement arrives in a later phase",
                        WalletResponse.PaymentMethod.COMING_SOON));
    }
}
