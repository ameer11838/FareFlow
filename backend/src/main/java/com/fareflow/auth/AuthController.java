package com.fareflow.auth;

import com.fareflow.auth.dto.AuthConfigResponse;
import com.fareflow.auth.dto.AuthResponse;
import com.fareflow.auth.dto.LoginRequest;
import com.fareflow.auth.dto.RegisterRequest;
import com.fareflow.profile.TravelProfileService;
import com.fareflow.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;
    private final AuthProperties authProperties;
    private final TravelProfileService travelProfileService;

    public AuthController(AuthService authService,
                          CurrentUserService currentUserService,
                          AuthProperties authProperties,
                          TravelProfileService travelProfileService) {
        this.authService = authService;
        this.currentUserService = currentUserService;
        this.authProperties = authProperties;
        this.travelProfileService = travelProfileService;
    }

    /**
     * Lets the client discover the server's mode instead of relying on its own
     * build flag, which could disagree.
     */
    @GetMapping("/config")
    public AuthConfigResponse config() {
        boolean enabled = authProperties.enabled();
        return new AuthConfigResponse(
                enabled,
                !enabled,
                enabled ? null : currentUserService.require().getName());
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * The authenticated identity. In demo mode this returns the seeded demo user,
     * so the frontend has one endpoint for "who am I" in both modes.
     *
     * <p>Carries {@code onboardingCompleted} so the app knows on first load whether
     * to route this rider to onboarding or straight to planning.
     *
     * <p>Logout is deliberately client-side: the token is stateless, so signing out
     * means discarding it. A server-side revocation list would need Redis, which is
     * out of scope for this phase.
     */
    @GetMapping("/me")
    public UserResponse me() {
        return travelProfileService.describe(currentUserService.require());
    }
}
