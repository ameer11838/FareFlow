package com.fareflow.insights;

import com.fareflow.auth.CurrentUserService;
import com.fareflow.insights.dto.InsightsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Always scoped to the authenticated user; there is no userId parameter. */
@RestController
@RequestMapping("/api/insights")
public class InsightsController {

    private final InsightsService insightsService;
    private final CurrentUserService currentUserService;

    public InsightsController(InsightsService insightsService, CurrentUserService currentUserService) {
        this.insightsService = insightsService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public InsightsResponse insights() {
        return insightsService.forCurrentWeek(currentUserService.require());
    }
}
