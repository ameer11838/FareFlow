package com.fareflow.insights;

import com.fareflow.auth.CurrentUserService;
import com.fareflow.insights.dto.InsightsResponse;
import com.fareflow.insights.dto.SpendingHistoryResponse;
import com.fareflow.exception.InvalidStateException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Always scoped to the authenticated user; there is no userId parameter. */
@RestController
@RequestMapping("/api/insights")
public class InsightsController {

    private final InsightsService insightsService;
    private final SpendingHistoryService spendingHistoryService;
    private final CurrentUserService currentUserService;

    public InsightsController(InsightsService insightsService,
                              SpendingHistoryService spendingHistoryService,
                              CurrentUserService currentUserService) {
        this.insightsService = insightsService;
        this.spendingHistoryService = spendingHistoryService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public InsightsResponse insights() {
        return insightsService.forCurrentWeek(currentUserService.require());
    }

    /**
     * Bucketed travel history for the charts.
     *
     * <p>An unrecognised range is rejected rather than silently defaulting: a
     * client asking for "6m" and getting 30 days back would draw a chart labelled
     * with a window it is not showing.
     */
    @GetMapping("/history")
    public SpendingHistoryResponse history(@RequestParam(required = false) String range) {
        HistoryRange resolved = range == null || range.isBlank()
                ? HistoryRange.defaultRange()
                : HistoryRange.parse(range).orElseThrow(() -> new InvalidStateException(
                        "Unknown range '%s'. Supported ranges: 7d, 30d, 3m, 1y".formatted(range)));
        return spendingHistoryService.history(currentUserService.require(), resolved);
    }
}
