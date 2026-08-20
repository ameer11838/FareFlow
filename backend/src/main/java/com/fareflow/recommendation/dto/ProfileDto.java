package com.fareflow.recommendation.dto;

import com.fareflow.recommendation.optimization.ContextProfile;

import java.util.Arrays;
import java.util.List;

/**
 * A selectable optimization stance. The weights are included for transparency —
 * the client displays them but never sends them; it sends only {@code id}.
 */
public record ProfileDto(
        String id,
        String displayName,
        String rationale,
        double costPriority,
        double timePriority,
        double transferPriority
) {

    public static ProfileDto from(ContextProfile profile) {
        return new ProfileDto(
                profile.name(),
                profile.displayName(),
                profile.rationale(),
                profile.costPriority(),
                profile.timePriority(),
                profile.transferPriority());
    }

    public static List<ProfileDto> all() {
        return Arrays.stream(ContextProfile.values()).map(ProfileDto::from).toList();
    }
}
