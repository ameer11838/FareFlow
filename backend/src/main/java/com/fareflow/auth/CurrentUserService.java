package com.fareflow.auth;

import com.fareflow.exception.ResourceNotFoundException;
import com.fareflow.user.User;
import com.fareflow.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single place the application decides <em>who is asking</em>.
 *
 * <p>This is the security boundary that matters most in a fintech app: no
 * controller accepts a {@code userId} from the browser for anything private.
 * Trips, ledger, wallet, insights, and budget all resolve identity here.
 *
 * <p>In auth mode the id comes from a verified JWT. In demo mode it comes from the
 * single seeded demo row — chosen by the server, never named by the client.
 */
@Service
@Transactional(readOnly = true)
public class CurrentUserService {

    private final UserRepository userRepository;
    private final AuthProperties authProperties;

    public CurrentUserService(UserRepository userRepository, AuthProperties authProperties) {
        this.userRepository = userRepository;
        this.authProperties = authProperties;
    }

    /**
     * @throws UnauthenticatedException when auth is on and no valid token was presented
     */
    public User require() {
        if (!authProperties.enabled()) {
            return demoUser();
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new UnauthenticatedException("Authentication is required");
        }

        return userRepository.findById(principal.userId())
                .orElseThrow(() -> new UnauthenticatedException("The authenticated user no longer exists"));
    }

    public long requireId() {
        return require().getId();
    }

    /**
     * Rejects any attempt to act on somebody else's data.
     *
     * <p>Used where a path still carries an id for readability. The id is checked
     * against the authenticated identity, never trusted on its own.
     */
    public User requireSelf(long requestedUserId) {
        User current = require();
        if (current.getId() != requestedUserId) {
            throw new ForbiddenException("You cannot access another user's data");
        }
        return current;
    }

    public boolean authEnabled() {
        return authProperties.enabled();
    }

    private User demoUser() {
        return userRepository.findByEmailIgnoreCase(authProperties.demoUserEmail())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Demo mode is enabled but no demo user (%s) is seeded"
                                .formatted(authProperties.demoUserEmail())));
    }
}
