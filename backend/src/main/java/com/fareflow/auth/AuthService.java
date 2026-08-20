package com.fareflow.auth;

import com.fareflow.auth.dto.AuthResponse;
import com.fareflow.auth.dto.LoginRequest;
import com.fareflow.auth.dto.RegisterRequest;
import com.fareflow.exception.InvalidStateException;
import com.fareflow.profile.TravelProfileService;
import com.fareflow.user.User;
import com.fareflow.user.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;
    /** Absent in demo mode, where no JwtService bean exists. */
    private final ObjectProvider<JwtService> jwtService;
    private final TravelProfileService travelProfileService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthProperties authProperties,
                       ObjectProvider<JwtService> jwtService,
                       TravelProfileService travelProfileService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authProperties = authProperties;
        this.jwtService = jwtService;
        this.travelProfileService = travelProfileService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        requireAuthEnabled();

        String email = request.email().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            // Deliberately the same wording a user would see for any duplicate --
            // this endpoint is unauthenticated, so it does leak that an address is
            // registered. Accepted: the alternative (silent success) makes the
            // signup flow unusable. Rate limiting is the real mitigation, later.
            throw new InvalidStateException("An account with that email already exists");
        }

        String timezone = request.timezoneOrDefault();
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Unknown timezone: " + timezone);
        }

        // Registration stays deliberately short: name, email, password. The budget
        // and every travel preference are asked for during onboarding, where there
        // is room to explain why FareFlow wants them. A signup form that opens with
        // "what is your weekly transportation budget?" loses people.
        String passwordHash = passwordEncoder.encode(request.password());
        String name = request.name().trim();

        User user = userRepository.save(request.weeklyBudgetCents() == null
                ? User.withoutBudget(name, email, timezone, passwordHash)
                : new User(name, email, request.weeklyBudgetCents(), timezone, passwordHash));

        return issue(user);
    }

    public AuthResponse login(LoginRequest request) {
        requireAuthEnabled();

        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElse(null);

        // Verify a hash even when the user is missing, so a wrong email and a wrong
        // password take comparable time and cannot be told apart by timing.
        String hash = user != null && user.canAuthenticate()
                ? user.getPasswordHash()
                : "$2a$10$invalidinvalidinvalidinvalidinvalidinvalidinvalidinvalidin";
        boolean matches = passwordEncoder.matches(request.password(), hash);

        if (user == null || !user.canAuthenticate() || !matches) {
            // One message for every failure: never reveal which half was wrong.
            throw new InvalidCredentialsException("Incorrect email or password");
        }

        return issue(user);
    }

    private AuthResponse issue(User user) {
        JwtService service = jwtService.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("JWT service unavailable while authentication is enabled");
        }
        return new AuthResponse(
                service.issueToken(user.getId(), user.getEmail()),
                service.expirationSeconds(),
                travelProfileService.describe(user));
    }

    private void requireAuthEnabled() {
        if (!authProperties.enabled()) {
            throw new InvalidStateException(
                    "Authentication is disabled; this server is running in demo mode");
        }
    }
}
