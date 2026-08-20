package com.fareflow.profile.dto;

import com.fareflow.profile.CommuteFrequency;
import com.fareflow.profile.CommuteKind;
import com.fareflow.profile.PassPreference;
import com.fareflow.recommendation.dto.ProfileDto;

import java.util.Arrays;
import java.util.List;

/**
 * The vocabularies onboarding is allowed to offer.
 *
 * <p>Served rather than hardcoded in the client for the same reason the scoring
 * weights are: the backend owns what a choice <em>means</em>. A frontend that
 * invented its own "4 days/week" option would be inventing an input to a financial
 * projection.
 */
public record ProfileOptionsResponse(
        List<ProfileDto> contextProfiles,
        List<Option> commuteFrequencies,
        List<Option> commuteKinds,
        List<Option> passPreferences,
        List<TravelProfileResponse.ModeOption> travelModes
) {

    /**
     * @param detail extra context for the card, or null when the label says it all
     */
    public record Option(String id, String displayName, String detail) {
    }

    /**
     * What each band means for the projection, in the band's own terms.
     *
     * <p>Says "projects from", not "about": the number is the conservative low end
     * of the band, and describing it as an average would be a small lie that a
     * rider could catch by doing the arithmetic themselves.
     */
    private static String detailFor(CommuteFrequency frequency) {
        if (frequency == CommuteFrequency.VARIES) {
            return "FareFlow will estimate from your actual trips";
        }
        int days = frequency.estimatedDaysPerWeek();
        return "FareFlow projects from %d commuting %s a week"
                .formatted(days, days == 1 ? "day" : "days");
    }

    public static ProfileOptionsResponse build() {
        return new ProfileOptionsResponse(
                ProfileDto.all(),
                Arrays.stream(CommuteFrequency.values())
                        .map(frequency -> new Option(frequency.name(), frequency.displayName(),
                                detailFor(frequency)))
                        .toList(),
                Arrays.stream(CommuteKind.values())
                        .map(kind -> new Option(kind.name(), kind.displayName(), null))
                        .toList(),
                Arrays.stream(PassPreference.values())
                        .map(preference -> new Option(preference.name(), preference.displayName(), null))
                        .toList(),
                TravelProfileResponse.allModes());
    }
}
