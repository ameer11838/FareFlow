package com.fareflow.budget;

import com.fareflow.common.WeekWindow;
import com.fareflow.ledger.LedgerService;
import com.fareflow.trip.TripRepository;
import com.fareflow.user.User;
import com.fareflow.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Derives weekly spending figures from the ledger.
 *
 * <p>There is no stored total anywhere. A mutable {@code total_spent} column can
 * drift on a partial failure, cannot be audited back to its constituent trips,
 * cannot be corrected retroactively, and races when two trips commit at once.
 * Summing rows has none of those problems.
 *
 * <p>At portfolio scale this aggregate takes microseconds. If it ever became slow,
 * the answer is a rollup table maintained alongside the writes — with the raw
 * entries still the source of truth. Derive first, materialise later, never the reverse.
 */
@Service
@Transactional(readOnly = true)
public class BudgetService {

    private final LedgerService ledgerService;
    private final TripRepository tripRepository;
    private final UserService userService;
    private final Clock clock;

    public BudgetService(LedgerService ledgerService,
                         TripRepository tripRepository,
                         UserService userService,
                         Clock clock) {
        this.ledgerService = ledgerService;
        this.tripRepository = tripRepository;
        this.userService = userService;
        this.clock = clock;
    }

    public WeeklySummary currentWeek(long userId) {
        User user = userService.getById(userId);
        WeekWindow week = WeekWindow.containing(clock.instant(), user.zoneId());
        return summarize(user, week);
    }

    public WeeklySummary summarize(User user, WeekWindow week) {
        long netCents = ledgerService.netAmountBetween(user.getId(), week.start(), week.end());

        // Ledger entries are negative for money out, so spending is the negation.
        long spentCents = -netCents;

        // A rider with no budget has no "remaining" and no utilization. Reporting
        // either as zero would invent a fact -- and a zero remaining balance reads
        // as "you are out of money", which is the opposite of the truth.
        Long budgetCents = user.getWeeklyBudgetCents();
        Long remainingCents = budgetCents == null ? null : budgetCents - spentCents;

        // Null rather than a division by zero when no budget is set, or when it is zero.
        Double utilization = budgetCents != null && budgetCents > 0
                ? (double) spentCents / (double) budgetCents
                : null;

        long tripCount = tripRepository.countCompletedBetween(user.getId(), week.start(), week.end());

        // Null propagates: the query excludes trips without a baseline, and returns
        // null when no trip in the window had one at all.
        Long savings = tripRepository.sumSavingsBetween(user.getId(), week.start(), week.end());

        return new WeeklySummary(week, budgetCents, spentCents, remainingCents,
                utilization, tripCount, savings);
    }
}
