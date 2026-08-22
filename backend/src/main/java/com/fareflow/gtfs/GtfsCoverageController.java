package com.fareflow.gtfs;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transit")
public class GtfsCoverageController {

    private final GtfsCoverageService coverageService;
    private final GtfsStopService stopService;

    public GtfsCoverageController(GtfsCoverageService coverageService, GtfsStopService stopService) {
        this.coverageService = coverageService;
        this.stopService = stopService;
    }

    @GetMapping("/coverage")
    public GtfsCoverageService.CoverageResponse coverage() {
        return coverageService.coverage();
    }

    /** Nearby markers backed only by imported GTFS stops and routes. */
    @GetMapping("/stops/nearby")
    public List<GtfsStopService.TransitStop> nearbyStops(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "1600") double radiusMetres,
            @RequestParam(defaultValue = "40") int limit) {
        return stopService.nearby(latitude, longitude, radiusMetres, limit);
    }
}
