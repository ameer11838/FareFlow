package com.fareflow.gtfs;

import com.google.transit.realtime.GtfsRealtime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Atomically replaces the fresh GTFS-Realtime TripUpdate overlay for one feed. */
@Service
public class GtfsRealtimeImporter {

    private static final DateTimeFormatter GTFS_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Set<String> TRIP_RELATIONSHIPS = Set.of(
            "SCHEDULED", "CANCELED", "ADDED", "UNSCHEDULED", "DUPLICATED", "DELETED");
    private static final Set<String> STOP_RELATIONSHIPS = Set.of(
            "SCHEDULED", "SKIPPED", "NO_DATA", "UNSCHEDULED");

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final long freshnessSeconds;

    public GtfsRealtimeImporter(JdbcTemplate jdbc, Clock clock,
                                @Value("${fareflow.gtfs.realtime-freshness-seconds:120}")
                                long freshnessSeconds) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.freshnessSeconds = Math.max(30, freshnessSeconds);
    }

    @Transactional(rollbackFor = Exception.class)
    public int importTripUpdates(String feedKey, InputStream input) throws IOException {
        Long feedId = jdbc.query("SELECT id FROM gtfs_feeds WHERE feed_key = ? AND status = 'READY'",
                result -> result.next() ? result.getLong(1) : null, feedKey);
        if (feedId == null) {
            throw new IllegalArgumentException("GTFS feed is not ready: " + feedKey);
        }

        GtfsRealtime.FeedMessage message = GtfsRealtime.FeedMessage.parseFrom(input);
        if (message.getHeader().hasIncrementality()
                && message.getHeader().getIncrementality()
                == GtfsRealtime.FeedHeader.Incrementality.DIFFERENTIAL) {
            throw new IllegalArgumentException(
                    "Differential GTFS-Realtime feeds are not supported without a retained delta history");
        }
        Instant observed = message.getHeader().hasTimestamp()
                ? Instant.ofEpochSecond(message.getHeader().getTimestamp()) : clock.instant();
        // An old publisher timestamp stays old. Refreshing an old payload must not
        // give it a brand-new freshness window.
        Instant expires = observed.plusSeconds(freshnessSeconds);
        List<Object[]> trips = new ArrayList<>();
        List<Object[]> stops = new ArrayList<>();

        for (GtfsRealtime.FeedEntity entity : message.getEntityList()) {
            if (!entity.hasTripUpdate()) {
                continue;
            }
            GtfsRealtime.TripUpdate update = entity.getTripUpdate();
            String tripId = update.getTrip().getTripId();
            if (tripId == null || tripId.isBlank()) {
                continue;
            }
            LocalDate startDate = parseDate(update.getTrip().getStartDate());
            String tripRelationship = update.getTrip().getScheduleRelationship().name();
            if (TRIP_RELATIONSHIPS.contains(tripRelationship)) {
                trips.add(new Object[]{feedId, tripId, sqlDate(startDate), tripRelationship,
                        Timestamp.from(observed), Timestamp.from(expires)});
            }
            for (GtfsRealtime.TripUpdate.StopTimeUpdate stop : update.getStopTimeUpdateList()) {
                String stopId = stop.hasStopId() && !stop.getStopId().isBlank()
                        ? stop.getStopId() : null;
                Integer sequence = stop.hasStopSequence() ? stop.getStopSequence() : null;
                if (stopId == null && sequence == null) {
                    continue;
                }
                String relationship = stop.getScheduleRelationship().name();
                if (!STOP_RELATIONSHIPS.contains(relationship)) {
                    continue;
                }
                stops.add(new Object[]{feedId, tripId, sqlDate(startDate), stopId, sequence,
                        delay(stop.hasArrival(), stop.getArrival()),
                        delay(stop.hasDeparture(), stop.getDeparture()),
                        time(stop.hasArrival(), stop.getArrival()),
                        time(stop.hasDeparture(), stop.getDeparture()), relationship,
                        Timestamp.from(observed), Timestamp.from(expires)});
            }
        }

        jdbc.update("DELETE FROM gtfs_realtime_stop_updates WHERE feed_id = ?", feedId);
        jdbc.update("DELETE FROM gtfs_realtime_trip_status WHERE feed_id = ?", feedId);
        jdbc.batchUpdate("""
                INSERT INTO gtfs_realtime_trip_status
                    (feed_id, trip_id, start_date, schedule_relationship, observed_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, trips);
        jdbc.batchUpdate("""
                INSERT INTO gtfs_realtime_stop_updates
                    (feed_id, trip_id, start_date, stop_id, stop_sequence,
                     arrival_delay_seconds, departure_delay_seconds, arrival_time, departure_time,
                     schedule_relationship, observed_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, stops);
        jdbc.update("""
                UPDATE gtfs_feeds SET realtime_updated_at = ?, realtime_expires_at = ?
                 WHERE id = ?
                """, Timestamp.from(observed), Timestamp.from(expires), feedId);
        return stops.size();
    }

    private static Integer delay(boolean present, GtfsRealtime.TripUpdate.StopTimeEvent event) {
        return present && event.hasDelay() ? event.getDelay() : null;
    }

    private static Timestamp time(boolean present, GtfsRealtime.TripUpdate.StopTimeEvent event) {
        return present && event.hasTime() ? Timestamp.from(Instant.ofEpochSecond(event.getTime())) : null;
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value, GTFS_DATE);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid GTFS-Realtime start_date: " + value, exception);
        }
    }

    private static Date sqlDate(LocalDate date) {
        return date == null ? null : Date.valueOf(date);
    }
}
