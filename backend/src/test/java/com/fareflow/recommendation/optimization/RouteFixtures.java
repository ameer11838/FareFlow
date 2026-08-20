package com.fareflow.recommendation.optimization;

import java.util.List;

/**
 * Shared candidate fixtures. The Newark set is the canonical worked example from
 * the design and is asserted on to four decimal places elsewhere.
 */
final class RouteFixtures {

    static final RouteCandidate NJ_TRANSIT =
            new RouteCandidate(1L, "NJ_TRANSIT", "NJ Transit", "RAIL", 22, 625, 0);
    static final RouteCandidate PATH =
            new RouteCandidate(2L, "PATH", "PATH", "SUBWAY", 38, 300, 0);
    static final RouteCandidate NYC_BUS =
            new RouteCandidate(3L, "NYC_BUS", "NYC Bus", "BUS", 55, 290, 0);

    /** Newark -> Manhattan: 22min/$6.25, 38min/$3.00, 55min/$2.90, all zero transfers. */
    static List<RouteCandidate> newarkToManhattan() {
        return List.of(NJ_TRANSIT, PATH, NYC_BUS);
    }

    static RouteCandidate route(long id, String name, int minutes, long cents, int transfers) {
        return new RouteCandidate(id, name.toUpperCase().replace(' ', '_'), name, "BUS",
                minutes, cents, transfers);
    }

    private RouteFixtures() {
    }
}
