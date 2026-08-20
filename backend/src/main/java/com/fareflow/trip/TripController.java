package com.fareflow.trip;

import com.fareflow.auth.CurrentUserService;
import com.fareflow.trip.dto.CreateTripRequest;
import com.fareflow.trip.dto.TripResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class TripController {

    private final TripService tripService;
    private final CurrentUserService currentUserService;

    public TripController(TripService tripService, CurrentUserService currentUserService) {
        this.tripService = tripService;
        this.currentUserService = currentUserService;
    }

    /**
     * The trip is always created for the authenticated user. A userId in the body
     * would be an authorization hole, so the DTO no longer has one.
     */
    @PostMapping("/trips")
    public ResponseEntity<TripResponse> takeTrip(@Valid @RequestBody CreateTripRequest request,
                                                 UriComponentsBuilder uriBuilder) {
        Trip trip = tripService.takeTrip(currentUserService.requireId(), request);
        return ResponseEntity
                .created(uriBuilder.path("/api/trips/{id}").build(trip.getId()))
                .body(TripResponse.from(trip));
    }

    @GetMapping("/trips/{id}")
    public TripResponse get(@PathVariable long id) {
        return TripResponse.from(tripService.getOwnedById(currentUserService.requireId(), id));
    }

    /**
     * Cancellation is a state transition that creates records, not a deletion —
     * the trip stays in history and gains a refund entry. Hence POST, not DELETE.
     */
    @PostMapping("/trips/{id}/cancel")
    public TripResponse cancel(@PathVariable long id) {
        return TripResponse.from(tripService.cancelTrip(currentUserService.requireId(), id));
    }

    /** Always the caller's own trips; there is no way to ask for anyone else's. */
    @GetMapping("/trips")
    public Map<String, Object> listMine(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        Page<Trip> trips = tripService.findForUser(
                currentUserService.requireId(), PageRequest.of(page, Math.min(size, 100)));
        return Map.of(
                "content", trips.getContent().stream().map(TripResponse::from).toList(),
                "page", trips.getNumber(),
                "size", trips.getSize(),
                "totalElements", trips.getTotalElements(),
                "totalPages", trips.getTotalPages());
    }
}
