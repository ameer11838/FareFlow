package com.fareflow.gtfs;

import com.fareflow.journey.TransitMode;

import java.util.Optional;

/** Strict allow-list for the public-transit modes FareFlow supports. */
public final class GtfsTransitMode {

    private GtfsTransitMode() {
    }

    /**
     * Maps standard and extended GTFS route types. Coach, taxi, air, car and
     * bike categories deliberately return empty instead of being approximated.
     */
    public static Optional<TransitMode> fromRouteType(int routeType) {
        return switch (routeType) {
            case 0 -> Optional.of(TransitMode.LIGHT_RAIL);
            case 1 -> Optional.of(TransitMode.SUBWAY);
            case 2 -> Optional.of(TransitMode.RAIL);
            case 3 -> Optional.of(TransitMode.BUS);
            case 4 -> Optional.of(TransitMode.FERRY);
            case 11 -> Optional.of(TransitMode.BUS);
            case 12 -> Optional.of(TransitMode.RAIL);
            default -> extended(routeType);
        };
    }

    private static Optional<TransitMode> extended(int routeType) {
        if (routeType >= 100 && routeType <= 199) {
            return Optional.of(TransitMode.RAIL);
        }
        if (routeType >= 200 && routeType <= 299) {
            return Optional.of(TransitMode.BUS);
        }
        if (routeType >= 300 && routeType <= 399) {
            return Optional.of(TransitMode.RAIL);
        }
        if (routeType == 401 || routeType == 402
                || (routeType >= 500 && routeType <= 699)) {
            return Optional.of(TransitMode.SUBWAY);
        }
        if (routeType == 405) {
            return Optional.of(TransitMode.RAIL);
        }
        if (routeType >= 400 && routeType <= 499) {
            return Optional.of(TransitMode.LIGHT_RAIL);
        }
        if (routeType >= 700 && routeType <= 899) {
            return Optional.of(TransitMode.BUS);
        }
        if (routeType >= 900 && routeType <= 999) {
            return Optional.of(TransitMode.LIGHT_RAIL);
        }
        if ((routeType >= 1000 && routeType <= 1099)
                || (routeType >= 1200 && routeType <= 1299)) {
            return Optional.of(TransitMode.FERRY);
        }
        return Optional.empty();
    }
}
