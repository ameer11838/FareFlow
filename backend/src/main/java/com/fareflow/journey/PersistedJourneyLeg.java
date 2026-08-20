package com.fareflow.journey;

import jakarta.persistence.*;

import java.util.List;

/**
 * One leg of a persisted journey.
 *
 * <p>Waypoints are stored as a compact "lat,lon;lat,lon" string. They are only ever
 * read back whole to draw a line, so a child table would add joins without buying
 * any queryability.
 */
@Entity
@Table(name = "journey_legs")
public class PersistedJourneyLeg {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Written by the owning journey's join column, not by this entity. */
    @Column(name = "journey_id", nullable = false, insertable = false, updatable = false)
    private Long journeyId;

    @Column(nullable = false, updatable = false) private int sequence;
    @Column(nullable = false, updatable = false) private String mode;
    @Column(updatable = false) private String agency;
    @Column(name = "line_name", nullable = false, updatable = false) private String lineName;
    @Column(name = "from_name", nullable = false, updatable = false) private String fromName;
    @Column(name = "to_name", nullable = false, updatable = false) private String toName;
    @Column(name = "duration_minutes", nullable = false, updatable = false) private int durationMinutes;
    @Column(name = "wait_minutes", nullable = false, updatable = false) private int waitMinutes;
    @Column(updatable = false) private String waypoints;

    protected PersistedJourneyLeg() {
    }

    static PersistedJourneyLeg snapshot(JourneyLeg leg, int sequence) {
        PersistedJourneyLeg entity = new PersistedJourneyLeg();
        entity.sequence = sequence;
        entity.mode = leg.mode().name();
        entity.agency = leg.agency();
        entity.lineName = leg.lineName();
        entity.fromName = leg.fromStopName();
        entity.toName = leg.toStopName();
        entity.durationMinutes = leg.durationMinutes();
        entity.waitMinutes = leg.waitMinutes();
        entity.waypoints = encode(leg.waypoints());
        return entity;
    }

    private static String encode(List<JourneyLeg.Waypoint> waypoints) {
        if (waypoints == null || waypoints.isEmpty()) {
            return null;
        }
        return waypoints.stream()
                .map(point -> "%s,%s,%s".formatted(point.latitude(), point.longitude(),
                        point.name() == null ? "" : point.name().replace(';', ' ').replace(',', ' ')))
                .reduce((a, b) -> a + ";" + b)
                .orElse(null);
    }

    public List<JourneyLeg.Waypoint> decodedWaypoints() {
        if (waypoints == null || waypoints.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(waypoints.split(";"))
                .map(entry -> entry.split(","))
                .filter(parts -> parts.length >= 2)
                .map(parts -> new JourneyLeg.Waypoint(
                        parts.length > 2 ? parts[2] : "",
                        Double.parseDouble(parts[0]),
                        Double.parseDouble(parts[1])))
                .toList();
    }

    public Long getId() { return id; }
    public int getSequence() { return sequence; }
    public String getMode() { return mode; }
    public String getAgency() { return agency; }
    public String getLineName() { return lineName; }
    public String getFromName() { return fromName; }
    public String getToName() { return toName; }
    public int getDurationMinutes() { return durationMinutes; }
    public int getWaitMinutes() { return waitMinutes; }
}
