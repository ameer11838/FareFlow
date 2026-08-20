package com.fareflow.passes;

import com.fareflow.auth.CurrentUserService;
import com.fareflow.passes.dto.PassRecommendation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Always scoped to the authenticated rider's own travel history. */
@RestController
@RequestMapping("/api/passes")
public class PassController {

    private final PassOptimizationService passService;
    private final CurrentUserService currentUserService;

    public PassController(PassOptimizationService passService, CurrentUserService currentUserService) {
        this.passService = passService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/recommendation")
    public PassRecommendation recommendation() {
        return passService.recommendFor(currentUserService.require());
    }
}
