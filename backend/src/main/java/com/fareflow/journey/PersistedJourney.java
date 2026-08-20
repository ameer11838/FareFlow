package com.fareflow.journey;

import com.fareflow.fare.FareCalculation;
import com.fareflow.location.LocationCandidate;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A journey as it was when the rider chose it.
 *
 * <p>Every field is a copy, not a reference. Schedules, fares, and the discovery
 * algorithm will all change; a trip taken today must still describe what was
 * actually bought after they do.
 */
@Entity
@Table(name = "journeys")
public class PersistedJourney {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "discovery_key", nullable = false, updatable = false)
    private String discoveryKey;

    @Column(name = "origin_display_name", nullable = false, updatable = false)
    private String originDisplayName;
    @Column(name = "destination_display_name", nullable = false, updatable = false)
    private String destinationDisplayName;
    @Column(name = "origin_latitude", nullable = false, updatable = false)
    private BigDecimal originLatitude;
    @Column(name = "origin_longitude", nullable = false, updatable = false)
    private BigDecimal originLongitude;
    @Column(name = "destination_latitude", nullable = false, updatable = false)
    private BigDecimal destinationLatitude;
    @Column(name = "destination_longitude", nullable = false, updatable = false)
    private BigDecimal destinationLongitude;

    @Column(name = "total_duration_minutes", nullable = false, updatable = false)
    private int totalDurationMinutes;
    @Column(name = "walking_minutes", nullable = false, updatable = false)
    private int walkingMinutes;
    @Column(nullable = false, updatable = false)
    private int transfers;

    /** Null when unpriced. The database enforces that this and status agree. */
    @Column(name = "total_fare_cents", updatable = false)
    private Long totalFareCents;
    @Column(name = "fare_status", nullable = false, updatable = false)
    private String fareStatus;
    @Column(name = "fare_source", nullable = false, updatable = false)
    private String fareSource;
    @Column(name = "fare_breakdown", updatable = false)
    private String fareBreakdown;

    @Column(name = "data_source", nullable = false, updatable = false)
    private String dataSource;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    /**
     * Owned by the journey via a join column, so Hibernate writes the foreign key
     * itself. {@code mappedBy} would need an association on the child; pointing it
     * at a plain column silently leaves journey_id null.
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "journey_id", nullable = false)
    @OrderBy("sequence ASC")
    private List<PersistedJourneyLeg> legs = new ArrayList<>();

    protected PersistedJourney() {
    }

    /** Builds the snapshot from a discovered journey and its authoritative fare. */
    public static PersistedJourney snapshot(Journey journey,
                                            FareCalculation fare,
                                            LocationCandidate origin,
                                            LocationCandidate destination) {
        PersistedJourney entity = new PersistedJourney();
        entity.discoveryKey = journey.id();
        entity.originDisplayName = origin.displayName();
        entity.destinationDisplayName = destination.displayName();
        entity.originLatitude = BigDecimal.valueOf(origin.latitude());
        entity.originLongitude = BigDecimal.valueOf(origin.longitude());
        entity.destinationLatitude = BigDecimal.valueOf(destination.latitude());
        entity.destinationLongitude = BigDecimal.valueOf(destination.longitude());
        entity.totalDurationMinutes = Math.max(1, journey.totalMinutes());
        entity.walkingMinutes = journey.walkingMinutes();
        entity.transfers = journey.transfers();
        entity.totalFareCents = fare.isPriced() ? fare.totalFareCents() : null;
        entity.fareStatus = fare.status().name();
        entity.fareSource = fare.source().name();
        entity.fareBreakdown = String.join("\n", fare.explanationLines());
        entity.dataSource = journey.dataSource();

        int sequence = 0;
        for (JourneyLeg leg : journey.legs()) {
            entity.legs.add(PersistedJourneyLeg.snapshot(leg, sequence++));
        }
        return entity;
    }

    public Long getId() { return id; }
    public String getDiscoveryKey() { return discoveryKey; }
    public String getOriginDisplayName() { return originDisplayName; }
    public String getDestinationDisplayName() { return destinationDisplayName; }
    public int getTotalDurationMinutes() { return totalDurationMinutes; }
    public int getWalkingMinutes() { return walkingMinutes; }
    public int getTransfers() { return transfers; }
    public Long getTotalFareCents() { return totalFareCents; }
    public String getFareStatus() { return fareStatus; }
    public String getFareSource() { return fareSource; }
    public List<String> getFareBreakdown() {
        return fareBreakdown == null || fareBreakdown.isBlank()
                ? List.of() : List.of(fareBreakdown.split("\n"));
    }
    public String getDataSource() { return dataSource; }
    public Instant getCreatedAt() { return createdAt; }
    public List<PersistedJourneyLeg> getLegs() { return legs; }

    /** "SEPTA → NJ Transit → Subway", rebuilt from the stored legs. */
    public String summary() {
        List<String> lines = legs.stream()
                .filter(leg -> !"WALK".equals(leg.getMode()))
                .map(PersistedJourneyLeg::getLineName)
                .toList();
        return lines.isEmpty() ? "Walk" : String.join(" → ", lines);
    }
}
