package com.fareflow.journey;

import com.fareflow.auth.CurrentUserService;
import com.fareflow.exception.ResourceNotFoundException;
import com.fareflow.trip.TripRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The stored itinerary behind a trip, for the expandable timeline on Trips.
 *
 * <p>Scoped to the caller: a journey is only readable through a trip that belongs
 * to them, so a guessed id reveals nothing.
 */
@RestController
@RequestMapping("/api/journeys")
public class JourneyDetailController {

    public record JourneyDetailResponse(
            long id,
            String originDisplayName,
            String destinationDisplayName,
            int totalDurationMinutes,
            int walkingMinutes,
            int transfers,
            Long totalFareCents,
            String fareStatus,
            String fareSource,
            List<String> fareBreakdown,
            String summary,
            List<LegResponse> legs
    ) {
        public record LegResponse(
                int sequence, String mode, String agency, String lineName,
                String fromName, String toName, int durationMinutes, int waitMinutes) {
        }
    }

    private final PersistedJourneyRepository journeyRepository;
    private final TripRepository tripRepository;
    private final CurrentUserService currentUserService;

    public JourneyDetailController(PersistedJourneyRepository journeyRepository,
                                   TripRepository tripRepository,
                                   CurrentUserService currentUserService) {
        this.journeyRepository = journeyRepository;
        this.tripRepository = tripRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public JourneyDetailResponse detail(@PathVariable long id) {
        long userId = currentUserService.requireId();

        // Ownership is established through the trip, not the journey itself.
        boolean owned = tripRepository.findByUserIdOrderByTakenAtDescIdDesc(
                        userId, org.springframework.data.domain.Pageable.unpaged())
                .stream()
                .anyMatch(trip -> id == (trip.getJourneyId() == null ? -1 : trip.getJourneyId()));

        if (!owned) {
            throw new ResourceNotFoundException("Journey %d was not found".formatted(id));
        }

        PersistedJourney journey = journeyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Journey %d was not found".formatted(id)));

        return new JourneyDetailResponse(
                journey.getId(),
                journey.getOriginDisplayName(),
                journey.getDestinationDisplayName(),
                journey.getTotalDurationMinutes(),
                journey.getWalkingMinutes(),
                journey.getTransfers(),
                journey.getTotalFareCents(),
                journey.getFareStatus(),
                journey.getFareSource(),
                journey.getFareBreakdown(),
                journey.summary(),
                journey.getLegs().stream()
                        .map(leg -> new JourneyDetailResponse.LegResponse(
                                leg.getSequence(), leg.getMode(), leg.getAgency(),
                                leg.getLineName(), leg.getFromName(), leg.getToName(),
                                leg.getDurationMinutes(), leg.getWaitMinutes()))
                        .toList());
    }
}
