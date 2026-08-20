package com.fareflow.profile;

import com.fareflow.profile.dto.UpdateTravelProfileRequest;
import com.fareflow.recommendation.optimization.ContextProfile;
import com.fareflow.user.User;
import com.fareflow.user.UserRepository;
import com.fareflow.user.dto.UserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads and writes a rider's travel profile, and decides which stance a request
 * should actually be scored with.
 *
 * <p>The precedence rule lives here, in one method, because it is the whole point
 * of collecting onboarding data:
 *
 * <pre>
 *   current context  >  onboarding default  >  BALANCED
 * </pre>
 *
 * <p>A rider whose default is SAVE_MONEY normally gets cost-leaning results. The
 * moment they say "I'm in a rush" for one trip, that wins — a stated habit should
 * never outrank a stated situation. Everything downstream (weights, scoring, the
 * ledger) is unchanged; only the stance selection moved.
 */
@Service
@Transactional(readOnly = true)
public class TravelProfileService {

    private final UserTravelProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public TravelProfileService(UserTravelProfileRepository profileRepository,
                                UserRepository userRepository,
                                Clock clock) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    public Optional<UserTravelProfile> find(long userId) {
        return profileRepository.findByUserId(userId);
    }

    /**
     * The rider's profile, or an unsaved empty one.
     *
     * <p>Deliberately does not persist: reading a profile must not create a row.
     * A rider who has never opened onboarding has no profile, and the honest
     * answer to "what are their preferences" is "none recorded yet".
     */
    public UserTravelProfile findOrEmpty(long userId) {
        return find(userId).orElseGet(() -> UserTravelProfile.emptyFor(userId));
    }

    public boolean isOnboardingCompleted(long userId) {
        return profileRepository.existsByUserIdAndOnboardingCompletedTrue(userId);
    }

    /**
     * The user as the API presents them, with the onboarding flag filled in.
     *
     * <p>Lives here rather than on {@code UserResponse} because only this service
     * knows the answer, and because putting the lookup in one place stops four
     * controllers from each deciding how to find it.
     */
    public UserResponse describe(User user) {
        return UserResponse.from(user, isOnboardingCompleted(user.getId()));
    }

    /**
     * The stance to score a request with.
     *
     * @param requestedProfile the {@code profile} parameter from this request, or
     *                         null/blank when the rider expressed nothing right now
     * @param userId           null for an anonymous quote, which has no default
     * @throws IllegalArgumentException when a profile name was supplied but is unknown
     */
    public ContextProfile resolveContextProfile(String requestedProfile, Long userId) {
        if (requestedProfile != null && !requestedProfile.isBlank()) {
            // The rider said something about *this* trip. That outranks any habit.
            return ContextProfile.parse(requestedProfile)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown profile '%s'. Valid profiles are: %s"
                                    .formatted(requestedProfile, ContextProfile.validNames())));
        }
        if (userId == null) {
            return ContextProfile.defaultProfile();
        }
        return find(userId)
                .map(UserTravelProfile::getDefaultContextProfile)
                .orElseGet(ContextProfile::defaultProfile);
    }

    /**
     * Replaces the profile, and writes the budget through to the user record.
     *
     * @param completeOnboarding true when this save is the onboarding submission
     *                           itself. Editing later never un-completes onboarding
     * @return the saved profile
     */
    @Transactional
    public UserTravelProfile save(User user, UpdateTravelProfileRequest request, boolean completeOnboarding) {
        UserTravelProfile profile = profileRepository.findByUserId(user.getId())
                .orElseGet(() -> new UserTravelProfile(user.getId()));

        profile.setDefaultContextProfile(parseContextProfile(request.defaultContextProfile()));
        profile.setWeeklyCommuteFrequency(parseFrequency(request.weeklyCommuteFrequency()));
        profile.setCommuteKind(parseCommuteKind(request.commuteKind()));
        profile.setPassPreference(parsePassPreference(request.passPreference()));
        profile.setPreferredModes(parseModes(request.preferredModes()));

        CommuteKind kind = profile.getCommuteKind();
        if (kind == CommuteKind.NONE) {
            // setCommuteKind already cleared the places; sending a commute alongside
            // "no regular commute" is a contradiction, not something to reconcile.
            profile.setTypicalCommute(TypicalPlace.empty(), TypicalPlace.empty());
        } else {
            profile.setTypicalCommute(
                    request.typicalOrigin() == null ? TypicalPlace.empty() : request.typicalOrigin().toDomain(),
                    request.typicalDestination() == null ? TypicalPlace.empty() : request.typicalDestination().toDomain());
        }

        if (completeOnboarding) {
            profile.completeOnboarding(clock.instant());
        }

        // The budget is stored on the user, not here: one budget, one place.
        // A null value clears it, which is what "I'm not sure" means.
        user.setWeeklyBudgetCents(request.weeklyBudgetCents());
        userRepository.save(user);

        return profileRepository.save(profile);
    }

    private static ContextProfile parseContextProfile(String raw) {
        if (raw == null || raw.isBlank()) {
            return ContextProfile.defaultProfile();
        }
        return ContextProfile.parse(raw)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown travel priority '%s'. Valid values are: %s"
                                .formatted(raw, ContextProfile.validNames())));
    }

    private static CommuteFrequency parseFrequency(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return CommuteFrequency.parse(raw)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown commute frequency '%s'. Valid values are: %s"
                                .formatted(raw, CommuteFrequency.validNames())));
    }

    private static CommuteKind parseCommuteKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return CommuteKind.parse(raw)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown commute type '%s'. Valid values are: %s"
                                .formatted(raw, CommuteKind.validNames())));
    }

    private static PassPreference parsePassPreference(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return PassPreference.parse(raw)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown payment style '%s'. Valid values are: %s"
                                .formatted(raw, PassPreference.validNames())));
    }

    private static List<PreferredTravelMode> parseModes(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        List<PreferredTravelMode> modes = new ArrayList<>();
        for (String value : raw) {
            modes.add(PreferredTravelMode.parse(value)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown travel mode '%s'. Valid values are: %s"
                                    .formatted(value, PreferredTravelMode.validNames()))));
        }
        return modes;
    }
}
