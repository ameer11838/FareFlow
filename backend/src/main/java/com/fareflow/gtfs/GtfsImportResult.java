package com.fareflow.gtfs;

import java.time.LocalDate;

public record GtfsImportResult(
        String feedKey,
        int agencies,
        int stops,
        int routes,
        int trips,
        int stopTimes,
        int transfers,
        int unsupportedRoutes,
        LocalDate serviceStart,
        LocalDate serviceEnd,
        String sha256
) {
}
