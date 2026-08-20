package com.fareflow.user;

import com.fareflow.auth.CurrentUserService;
import com.fareflow.profile.TravelProfileService;
import com.fareflow.user.dto.UpdateBudgetRequest;
import com.fareflow.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * HTTP surface for users. Validation, delegation, and status codes only —
 * no business logic.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final CurrentUserService currentUserService;
    private final TravelProfileService travelProfileService;

    public UserController(UserService userService,
                          CurrentUserService currentUserService,
                          TravelProfileService travelProfileService) {
        this.userService = userService;
        this.currentUserService = currentUserService;
        this.travelProfileService = travelProfileService;
    }

    /**
     * The caller's own profile. There is deliberately no endpoint that lists users
     * or fetches one by id — those existed before authentication and would leak
     * every account's name, email, and budget.
     */
    @GetMapping("/me")
    public UserResponse me() {
        return travelProfileService.describe(currentUserService.require());
    }

    @PatchMapping("/me/budget")
    public UserResponse updateBudget(@Valid @RequestBody UpdateBudgetRequest request) {
        return travelProfileService.describe(userService.updateWeeklyBudget(
                currentUserService.requireId(), request.weeklyBudgetCents()));
    }
}
