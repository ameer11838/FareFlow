package com.fareflow.budget;

import com.fareflow.auth.CurrentUserService;
import com.fareflow.budget.dto.DashboardResponse;
import com.fareflow.profile.TravelProfileService;
import com.fareflow.trip.TripService;
import com.fareflow.trip.dto.TripResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final BudgetService budgetService;
    private final TripService tripService;
    private final CurrentUserService currentUserService;
    private final TravelProfileService travelProfileService;

    public DashboardController(BudgetService budgetService,
                               TripService tripService,
                               CurrentUserService currentUserService,
                               TravelProfileService travelProfileService) {
        this.budgetService = budgetService;
        this.tripService = tripService;
        this.currentUserService = currentUserService;
        this.travelProfileService = travelProfileService;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        var current = currentUserService.require();
        WeeklySummary summary = budgetService.currentWeek(current.getId());
        List<TripResponse> recent = tripService.findRecentForUser(current.getId()).stream()
                .map(TripResponse::from)
                .toList();
        return DashboardResponse.of(travelProfileService.describe(current), summary, recent);
    }
}
