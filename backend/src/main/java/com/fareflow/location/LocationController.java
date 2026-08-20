package com.fareflow.location;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Location autocomplete.
 *
 * <p>Public: a place name is not private data, and the search box must work before
 * anyone signs in. Nothing here touches a user's money.
 */
@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping
    public List<LocationCandidate> search(
            @RequestParam @NotBlank(message = "q is required") String q,
            @RequestParam(defaultValue = "6") int limit) {
        return locationService.search(q, limit);
    }
}
