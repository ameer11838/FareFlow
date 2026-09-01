package com.fareflow.profile;

import com.fareflow.recommendation.optimization.ContextProfile;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * What FareFlow learned about a rider during onboarding.
 *
 * <p>Every field except the default stance is optional, because every onboarding
 * question can be skipped. An absent answer stays absent: nothing here is
 * back-filled with a plausible-looking default, since a made-up commute would
 * drive real recommendations.
 *
 * <p><strong>No budget field.</strong> The weekly transportation budget lives on
 * {@code users} and is read from there by the ledger, wallet, insights, and budget
 * pressure weighting. Duplicating it here would create a second number that could
 * disagree with the one the money actually flows against.
 */
@Entity
@Table(name = "user_travel_profiles")
public class UserTravelProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private Long userId;

    /**
     * The rider's standing stance. Stored by name — the weights themselves stay in
     * {@link ContextProfile}, so retuning a profile never needs a data migration.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "default_context_profile", nullable = false)
    private ContextProfile defaultContextProfile = ContextProfile.defaultProfile();

    @Enumerated(EnumType.STRING)
    @Column(name = "weekly_commute_frequency")
    private CommuteFrequency weeklyCommuteFrequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "commute_kind")
    private CommuteKind commuteKind;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "name", column = @Column(name = "typical_origin_name")),
            @AttributeOverride(name = "latitude", column = @Column(name = "typical_origin_lat")),
            @AttributeOverride(name = "longitude", column = @Column(name = "typical_origin_lon")),
            @AttributeOverride(name = "providerPlaceId", column = @Column(name = "typical_origin_place_id")),
    })
    private TypicalPlace typicalOrigin = TypicalPlace.empty();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "name", column = @Column(name = "typical_destination_name")),
            @AttributeOverride(name = "latitude", column = @Column(name = "typical_destination_lat")),
            @AttributeOverride(name = "longitude", column = @Column(name = "typical_destination_lon")),
            @AttributeOverride(name = "providerPlaceId", column = @Column(name = "typical_destination_place_id")),
    })
    private TypicalPlace typicalDestination = TypicalPlace.empty();

    @Enumerated(EnumType.STRING)
    @Column(name = "pass_preference")
    private PassPreference passPreference;

    @Enumerated(EnumType.STRING)
    @Column(name = "fare_category", nullable = false)
    private FareCategory fareCategory = FareCategory.REGULAR;

    /**
     * Eager because it is a bounded set of at most five rows that every caller
     * needs, and {@code open-in-view} is off — a lazy collection would fail during
     * serialization rather than merely cost a query.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_travel_profile_modes",
            joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "mode", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<PreferredTravelMode> preferredModes = EnumSet.noneOf(PreferredTravelMode.class);

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted = false;

    @Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;

    protected UserTravelProfile() {
        // required by JPA
    }

    public UserTravelProfile(long userId) {
        this.userId = userId;
    }

    /**
     * A profile for a rider who has not been asked anything yet.
     *
     * <p>Not persisted: reading a profile must not create one. A GET that writes is
     * a surprise in the logs and a write amplification problem later.
     */
    public static UserTravelProfile emptyFor(long userId) {
        return new UserTravelProfile(userId);
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public void setDefaultContextProfile(ContextProfile profile) {
        this.defaultContextProfile = profile == null ? ContextProfile.defaultProfile() : profile;
    }

    public void setWeeklyCommuteFrequency(CommuteFrequency frequency) {
        this.weeklyCommuteFrequency = frequency;
    }

    public void setCommuteKind(CommuteKind kind) {
        this.commuteKind = kind;
        // "No regular commute" and a saved commute cannot both be true.
        if (kind == CommuteKind.NONE) {
            this.typicalOrigin = TypicalPlace.empty();
            this.typicalDestination = TypicalPlace.empty();
        }
    }

    /**
     * Sets the saved commute. Both ends or neither: a commute with only a start is
     * not a commute, and storing half of one would produce a shortcut that cannot
     * be planned.
     */
    public void setTypicalCommute(TypicalPlace origin, TypicalPlace destination) {
        TypicalPlace from = origin == null ? TypicalPlace.empty() : origin;
        TypicalPlace to = destination == null ? TypicalPlace.empty() : destination;

        if (from.isPresent() != to.isPresent()) {
            throw new IllegalArgumentException(
                    "A typical commute needs both a starting point and a destination");
        }
        this.typicalOrigin = from;
        this.typicalDestination = to;
    }

    public void setPassPreference(PassPreference passPreference) {
        this.passPreference = passPreference;
    }

    public void setFareCategory(FareCategory fareCategory) {
        this.fareCategory = fareCategory == null ? FareCategory.REGULAR : fareCategory;
    }

    public void setPreferredModes(Collection<PreferredTravelMode> modes) {
        Set<PreferredTravelMode> replacement = EnumSet.noneOf(PreferredTravelMode.class);
        if (modes != null) {
            replacement.addAll(modes);
        }
        // Mutate in place rather than reassigning: Hibernate tracks the collection
        // instance it handed us, and swapping it out orphans the change.
        this.preferredModes.clear();
        this.preferredModes.addAll(replacement);
    }

    /**
     * Marks onboarding finished. Idempotent — finishing twice must not move the
     * timestamp, which is the record of when the rider actually answered.
     */
    public void completeOnboarding(Instant at) {
        if (!onboardingCompleted) {
            this.onboardingCompleted = true;
            this.onboardingCompletedAt = at;
        }
    }

    public boolean hasTypicalCommute() {
        return typicalOrigin.isPresent() && typicalDestination.isPresent();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public ContextProfile getDefaultContextProfile() {
        return defaultContextProfile;
    }

    public CommuteFrequency getWeeklyCommuteFrequency() {
        return weeklyCommuteFrequency;
    }

    public CommuteKind getCommuteKind() {
        return commuteKind;
    }

    public TypicalPlace getTypicalOrigin() {
        return typicalOrigin;
    }

    public TypicalPlace getTypicalDestination() {
        return typicalDestination;
    }

    public PassPreference getPassPreference() {
        return passPreference;
    }

    public FareCategory getFareCategory() {
        return fareCategory == null ? FareCategory.REGULAR : fareCategory;
    }

    public Set<PreferredTravelMode> getPreferredModes() {
        return Set.copyOf(preferredModes);
    }

    public boolean isOnboardingCompleted() {
        return onboardingCompleted;
    }

    public Instant getOnboardingCompletedAt() {
        return onboardingCompletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
