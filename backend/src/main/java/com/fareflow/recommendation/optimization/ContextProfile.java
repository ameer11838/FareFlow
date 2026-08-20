package com.fareflow.recommendation.optimization;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Named optimization stances the user can pick from.
 *
 * <p><strong>The backend owns these weights, not the client.</strong> The API accepts
 * a profile <em>name</em> and looks the numbers up here; it never accepts raw weights
 * over the wire. That keeps every financial trade-off decision inside the Java engine
 * and means a malicious or buggy client cannot skew a recommendation.
 *
 * <p>This is also the shape a natural-language component will eventually target: a
 * model would classify "I'm running late for an interview" into {@link #RUSH}, or
 * produce a sanitised custom weight vector — and the deterministic scorer downstream
 * would be completely unchanged.
 */
public enum ContextProfile {

    BALANCED(0.45, 0.45, 0.10,
            "Balanced",
            "weighing cost and travel time equally"),

    RUSH(0.15, 0.75, 0.10,
            "I'm in a rush",
            "prioritizing travel time over cost"),

    SAVE_MONEY(0.75, 0.15, 0.10,
            "Save me money",
            "prioritizing fare over travel time"),

    FEWER_TRANSFERS(0.25, 0.25, 0.50,
            "Fewer transfers",
            "prioritizing direct routes over cost and time");

    private final double costPriority;
    private final double timePriority;
    private final double transferPriority;
    private final String displayName;
    private final String rationale;

    ContextProfile(double costPriority, double timePriority, double transferPriority,
                   String displayName, String rationale) {
        this.costPriority = costPriority;
        this.timePriority = timePriority;
        this.transferPriority = transferPriority;
        this.displayName = displayName;
        this.rationale = rationale;
    }

    /** The default stance when the user expresses no preference. */
    public static ContextProfile defaultProfile() {
        return BALANCED;
    }

    /**
     * Parses a profile name case-insensitively. Returns empty for unknown values so
     * the caller can reject with a 400 listing the valid options.
     */
    public static Optional<ContextProfile> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.of(defaultProfile());
        }
        String normalised = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return Arrays.stream(values())
                .filter(profile -> profile.name().equals(normalised))
                .findFirst();
    }

    public static String validNames() {
        return String.join(", ", Arrays.stream(values()).map(Enum::name).toList());
    }

    /** Base weights for this stance, before any budget-pressure adjustment. */
    public OptimizationWeights baseWeights() {
        return new OptimizationWeights(costPriority, timePriority, transferPriority,
                this == BALANCED ? WeightSource.DEFAULT : WeightSource.PROFILE, 0.0);
    }

    public double costPriority() {
        return costPriority;
    }

    public double timePriority() {
        return timePriority;
    }

    public double transferPriority() {
        return transferPriority;
    }

    public String displayName() {
        return displayName;
    }

    /** Fragment used in explanations: "…because you asked FareFlow to save you money". */
    public String rationale() {
        return rationale;
    }
}
