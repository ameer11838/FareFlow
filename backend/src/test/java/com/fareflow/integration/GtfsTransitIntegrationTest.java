package com.fareflow.integration;

import com.fareflow.gtfs.GtfsCoverageService;
import com.fareflow.gtfs.GtfsImportResult;
import com.fareflow.gtfs.GtfsRealtimeImporter;
import com.fareflow.gtfs.GtfsRouteDiscoveryProvider;
import com.fareflow.gtfs.GtfsScheduleImporter;
import com.fareflow.gtfs.GtfsStopService;
import com.fareflow.journey.Journey;
import com.fareflow.journey.TransitMode;
import com.fareflow.location.LocationCandidate;
import com.fareflow.location.LocationService;
import com.google.transit.realtime.GtfsRealtime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class GtfsTransitIntegrationTest extends IntegrationTestBase {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    @Autowired
    private GtfsScheduleImporter importer;

    @Autowired
    private GtfsRealtimeImporter realtimeImporter;

    @Autowired
    private GtfsRouteDiscoveryProvider router;

    @Autowired
    private GtfsCoverageService coverageService;

    @Autowired
    private GtfsStopService stopService;

    @Autowired
    private LocationService locationService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @TempDir
    Path temporary;

    @Test
    void importsAndNormalizesOnlyFareFlowTransitModes() throws Exception {
        GtfsImportResult result = importer.importFeed("mbta", fixture());

        assertThat(result.agencies()).isEqualTo(2);
        assertThat(result.routes()).isEqualTo(2);
        assertThat(result.trips()).isEqualTo(2);
        assertThat(result.unsupportedRoutes()).isEqualTo(1);
        assertThat(jdbc.queryForList(
                "SELECT transit_mode FROM gtfs_routes ORDER BY transit_mode", String.class))
                .containsExactly("BUS", "RAIL");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM gtfs_trips WHERE trip_id = 'CAR_TRIP'", Integer.class))
                .isZero();

        var feed = coverageService.coverage().feeds().stream()
                .filter(item -> item.feedKey().equals("mbta")).findFirst().orElseThrow();
        assertThat(feed.supported()).isTrue();
        assertThat(feed.agencies()).containsExactly("City Bus", "Regional Rail");
        assertThat(feed.modes()).containsExactly("BUS", "RAIL");
    }

    @Test
    void routesAcrossAgenciesUsingPublishedStopTimes() throws Exception {
        importer.importFeed("mbta", fixture());

        Journey journey = router.discover(place("Origin", 42.3500, -71.0600),
                        place("Destination", 42.3900, -71.1000))
                .stream().findFirst().orElseThrow();

        assertThat(journey.dataSource()).isEqualTo(Journey.DataSource.GTFS_SCHEDULE);
        assertThat(journey.transitLegs()).extracting(leg -> leg.mode())
                .containsExactly(TransitMode.BUS, TransitMode.RAIL);
        assertThat(journey.agencies()).containsExactly("City Bus", "Regional Rail");
        assertThat(journey.transfers()).isEqualTo(1);
        assertThat(journey.transitLegs()).allSatisfy(leg -> {
            assertThat(leg.lineCode()).startsWith("GTFS:mbta:");
            assertThat(leg.waitMinutes()).isNotNegative();
            assertThat(leg.waypoints()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(leg.departureTime()).isNotNull();
            assertThat(leg.arrivalTime()).isAfter(leg.departureTime());
            assertThat(leg.stopCount()).isPositive();
        });
    }

    @Test
    void importedStopsBecomeSearchableWithoutFabricatingUnimportedCoverage() throws Exception {
        assertThat(stopService.searchLocations("Origin Stop", 6)).isEmpty();
        importer.importFeed("mbta", fixture());

        var matches = stopService.searchLocations("Origin Stop", 6);

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().displayName()).isEqualTo("Origin Stop — Greater Boston");
        assertThat(matches.getFirst().source()).isEqualTo("GTFS");
        assertThat(matches.getFirst().providerPlaceId()).isEqualTo("gtfs:mbta:ORIGIN");
        assertThat(locationService.resolve(matches.getFirst().displayName()).orElseThrow())
                .extracting(LocationCandidate::latitude, LocationCandidate::longitude,
                        LocationCandidate::source)
                .containsExactly(42.3500, -71.0600, "GTFS");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/locations").param("q", "Origin Stop"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[0].source").value("GTFS"));
    }

    @Test
    void nearbyStopEndpointReturnsOnlyImportedModesOperatorsAndLines() throws Exception {
        importer.importFeed("mbta", fixture());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/transit/stops/nearby")
                        .param("latitude", "42.3500")
                        .param("longitude", "-71.0600")
                        .param("radiusMetres", "500")
                        .param("limit", "10"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[0].name").value("Origin Stop"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[0].modes[0]").value("BUS"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[0].operators[0]").value("City Bus"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[0].lines[0]").value("B1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[0].source").value("GTFS"));
    }

    @Test
    void freshRealtimeCancellationRemovesTripWithoutAssumingOtherTripsAreOnTime() throws Exception {
        importer.importFeed("mbta", fixture());
        byte[] message = canceledTripUpdate("BUS_TRIP");

        realtimeImporter.importTripUpdates("mbta", new ByteArrayInputStream(message));

        assertThat(jdbc.queryForObject("""
                SELECT schedule_relationship FROM gtfs_realtime_trip_status
                 WHERE trip_id = 'BUS_TRIP'
                """, String.class)).isEqualTo("CANCELED");
        assertThat(router.discover(place("Origin", 42.3500, -71.0600),
                place("Destination", 42.3900, -71.1000))).isEmpty();
    }

    private Path fixture() throws IOException {
        var now = clock.instant().atZone(NEW_YORK);
        LocalDate today = now.toLocalDate();
        int start = now.toLocalTime().toSecondOfDay() + 300;
        Map<String, String> files = new LinkedHashMap<>();
        files.put("agency.txt", """
                agency_id,agency_name,agency_url,agency_timezone
                BUS_AGENCY,City Bus,https://example.test/bus,America/New_York
                RAIL_AGENCY,Regional Rail,https://example.test/rail,America/New_York
                """);
        files.put("stops.txt", """
                stop_id,stop_name,stop_lat,stop_lon,location_type
                ORIGIN,Origin Stop,42.3500,-71.0600,0
                HUB,Central Hub,42.3700,-71.0800,0
                DEST,Destination Stop,42.3900,-71.1000,0
                """);
        files.put("routes.txt", """
                route_id,agency_id,route_short_name,route_long_name,route_type
                BUS_ROUTE,BUS_AGENCY,B1,Crosstown Bus,3
                RAIL_ROUTE,RAIL_AGENCY,R1,Regional Train,2
                CAR_ROUTE,BUS_AGENCY,C1,Unsupported Car Service,1501
                """);
        files.put("calendar.txt", """
                service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date
                DAILY,1,1,1,1,1,1,1,%s,%s
                """.formatted(today.minusDays(1).format(DATE), today.plusDays(1).format(DATE)));
        files.put("trips.txt", """
                route_id,service_id,trip_id,trip_headsign,direction_id
                BUS_ROUTE,DAILY,BUS_TRIP,Central Hub,0
                RAIL_ROUTE,DAILY,RAIL_TRIP,Destination,0
                CAR_ROUTE,DAILY,CAR_TRIP,Nowhere,0
                """);
        files.put("stop_times.txt", """
                trip_id,arrival_time,departure_time,stop_id,stop_sequence,pickup_type,drop_off_type
                BUS_TRIP,%s,%s,ORIGIN,1,0,0
                BUS_TRIP,%s,%s,HUB,2,0,0
                RAIL_TRIP,%s,%s,HUB,1,0,0
                RAIL_TRIP,%s,%s,DEST,2,0,0
                CAR_TRIP,%s,%s,ORIGIN,1,0,0
                CAR_TRIP,%s,%s,DEST,2,0,0
                """.formatted(time(start), time(start), time(start + 600), time(start + 600),
                time(start + 900), time(start + 900), time(start + 1_800), time(start + 1_800),
                time(start), time(start), time(start + 300), time(start + 300)));
        files.put("transfers.txt", """
                from_stop_id,to_stop_id,transfer_type,min_transfer_time
                HUB,HUB,2,120
                """);

        Path zip = temporary.resolve("fixture.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                output.putNextEntry(new ZipEntry(file.getKey()));
                output.write(file.getValue().stripIndent().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return zip;
    }

    private byte[] canceledTripUpdate(String tripId) {
        LocalDate date = clock.instant().atZone(NEW_YORK).toLocalDate();
        var trip = GtfsRealtime.TripDescriptor.newBuilder()
                .setTripId(tripId)
                .setStartDate(date.format(DATE))
                .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.CANCELED);
        var update = GtfsRealtime.TripUpdate.newBuilder().setTrip(trip);
        var entity = GtfsRealtime.FeedEntity.newBuilder().setId("cancel-1").setTripUpdate(update);
        var header = GtfsRealtime.FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("2.0")
                .setTimestamp(clock.instant().getEpochSecond());
        return GtfsRealtime.FeedMessage.newBuilder().setHeader(header).addEntity(entity)
                .build().toByteArray();
    }

    private static String time(int totalSeconds) {
        int hours = totalSeconds / 3_600;
        int minutes = totalSeconds % 3_600 / 60;
        int seconds = totalSeconds % 60;
        return "%02d:%02d:%02d".formatted(hours, minutes, seconds);
    }

    private static LocationCandidate place(String name, double latitude, double longitude) {
        return new LocationCandidate("test:" + name, name, "Boston", "MA", "US",
                latitude, longitude, "Place", "TEST");
    }
}
