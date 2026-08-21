package com.fareflow.gtfs;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transit")
public class GtfsCoverageController {

    private final GtfsCoverageService coverageService;

    public GtfsCoverageController(GtfsCoverageService coverageService) {
        this.coverageService = coverageService;
    }

    @GetMapping("/coverage")
    public GtfsCoverageService.CoverageResponse coverage() {
        return coverageService.coverage();
    }
}
