package com.fareflow.route;

/**
 * Transit operators. Persisted as their enum name (see {@code V1__create_transit_routes.sql}),
 * with a separate human-readable label for display and explanations.
 */
public enum TransitProvider {

    NJ_TRANSIT("NJ Transit"),
    PATH("PATH"),
    NYC_BUS("NYC Bus"),
    NY_WATERWAY("NY Waterway");

    private final String displayName;

    TransitProvider(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
