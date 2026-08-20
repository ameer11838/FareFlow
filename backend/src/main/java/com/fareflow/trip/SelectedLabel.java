package com.fareflow.trip;

/**
 * Which recommendation the user acted on. {@code MANUAL} covers a route chosen
 * directly rather than from a labelled recommendation.
 */
public enum SelectedLabel {
    CHEAPEST,
    FASTEST,
    BEST_VALUE,
    MANUAL
}
