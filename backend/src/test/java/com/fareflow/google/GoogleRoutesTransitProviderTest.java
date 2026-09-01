package com.fareflow.google;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fareflow.journey.Journey;
import com.fareflow.journey.TransitMode;
import com.fareflow.location.LocationCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleRoutesTransitProviderTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-08-22T12:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("requests TRANSIT alternatives and normalizes Google's route facts")
    void requestsAndNormalizesTransitRoutes() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://routes.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://routes.googleapis.com/directions/v2:computeRoutes"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "maps-test-key"))
                .andExpect(header("X-Goog-FieldMask", org.hamcrest.Matchers.containsString(
                        "routes.legs.steps.transitDetails.stopDetails")))
                .andExpect(jsonPath("$.travelMode").value("TRANSIT"))
                .andExpect(jsonPath("$.computeAlternativeRoutes").value(true))
                .andExpect(jsonPath("$.polylineEncoding").value("GEO_JSON_LINESTRING"))
                .andExpect(jsonPath("$.regionCode").value("US"))
                .andRespond(withSuccess(validRouteResponse(true), MediaType.APPLICATION_JSON));

        GoogleRoutesTransitProvider provider = new GoogleRoutesTransitProvider(
                builder.build(), "maps-test-key", Clock.fixed(REQUESTED_AT, ZoneOffset.UTC));

        List<Journey> journeys = provider.discover(origin(), destination());

        assertThat(journeys).hasSize(1);
        Journey journey = journeys.getFirst();
        assertThat(journey.dataSource()).isEqualTo(Journey.DataSource.GOOGLE_ROUTES);
        assertThat(journey.id()).startsWith("GOOGLE:");
        assertThat(journey.totalMinutes()).isEqualTo(24);
        assertThat(journey.walkingMinutes()).isEqualTo(6);
        assertThat(journey.providerFare()).isEqualTo(
                new Journey.ProviderFare(250, "USD", "Google Maps"));
        assertThat(journey.legs()).extracting(leg -> leg.mode())
                .containsExactly(TransitMode.WALK, TransitMode.BUS, TransitMode.WALK);

        var ride = journey.transitLegs().getFirst();
        assertThat(ride.agency()).isEqualTo("NJ TRANSIT");
        assertThat(ride.lineName()).isEqualTo("62 toward Newark Penn Station");
        assertThat(ride.fromStopName()).isEqualTo("NJIT");
        assertThat(ride.toStopName()).isEqualTo("Newark Penn Station");
        assertThat(ride.departureTime()).isEqualTo(Instant.parse("2026-08-22T12:07:00Z"));
        assertThat(ride.arrivalTime()).isEqualTo(Instant.parse("2026-08-22T12:22:00Z"));
        assertThat(ride.waitMinutes()).isEqualTo(3);
        assertThat(ride.stopCount()).isEqualTo(5);
        assertThat(ride.realtime()).isFalse();
        assertThat(ride.waypoints()).hasSizeGreaterThan(2);
        assertThat(ride.waypoints().stream()
                .filter(point -> point.name() != null && !point.name().isBlank())
                .map(com.fareflow.journey.JourneyLeg.Waypoint::name))
                .containsExactly(
                        "NJIT",
                        "62 · stop 1 of 5",
                        "62 · stop 2 of 5",
                        "62 · stop 3 of 5",
                        "62 · stop 4 of 5",
                        "Newark Penn Station");
        server.verify();
    }

    @Test
    @DisplayName("omits an unavailable Google fare instead of inventing one")
    void missingFareStaysUnknown() throws Exception {
        GoogleRoutesTransitProvider provider = parser();
        List<Journey> journeys = provider.parseResponse(
                JSON.readTree(validRouteResponse(false)), origin(), destination(), REQUESTED_AT);

        assertThat(journeys).hasSize(1);
        assertThat(journeys.getFirst().providerFare()).isNull();
    }

    @Test
    @DisplayName("rejects transit vehicles outside FareFlow's public-transit scope")
    void rejectsOutOfScopeVehicle() throws Exception {
        String response = validRouteResponse(true).replace("\"BUS\"", "\"CABLE_CAR\"");

        assertThat(parser().parseResponse(
                JSON.readTree(response), origin(), destination(), REQUESTED_AT)).isEmpty();
    }

    @Test
    @DisplayName("requires a real provider stop count for stop-based usage pricing")
    void refusesToInventStopCount() throws Exception {
        String response = validRouteResponse(true).replace("\"stopCount\": 5,", "");

        assertThat(parser().parseResponse(
                JSON.readTree(response), origin(), destination(), REQUESTED_AT)).isEmpty();
    }

    @Test
    @DisplayName("an empty Google routes array is an honest empty result")
    void emptyRoutesStayEmpty() throws Exception {
        assertThat(parser().parseResponse(
                JSON.readTree("{\"routes\":[]}"), origin(), destination(), REQUESTED_AT)).isEmpty();
    }

    private static GoogleRoutesTransitProvider parser() {
        return new GoogleRoutesTransitProvider(RestClient.create(), "test",
                Clock.fixed(REQUESTED_AT, ZoneOffset.UTC));
    }

    private static LocationCandidate origin() {
        return new LocationCandidate("origin", "NJIT", "Newark", "NJ", "US",
                40.7420, -74.1780, "POI", "TEST");
    }

    private static LocationCandidate destination() {
        return new LocationCandidate("destination", "Newark Penn Station", "Newark", "NJ", "US",
                40.7347, -74.1642, "TRANSIT_STOP", "TEST");
    }

    private static String validRouteResponse(boolean includeFare) {
        String fare = includeFare
                ? "\"travelAdvisory\":{\"transitFare\":{\"currencyCode\":\"USD\",\"units\":\"2\",\"nanos\":500000000}},"
                : "";
        return """
                {"routes":[{%s"legs":[{"steps":[
                  {"travelMode":"WALK","staticDuration":"240s","distanceMeters":320,
                   "polyline":{"geoJsonLinestring":{"type":"LineString","coordinates":[[-74.1780,40.7420],[-74.1760,40.7410]]}},
                   "startLocation":{"latLng":{"latitude":40.7420,"longitude":-74.1780}},
                   "endLocation":{"latLng":{"latitude":40.7410,"longitude":-74.1760}}},
                  {"travelMode":"TRANSIT","staticDuration":"900s","distanceMeters":4200,
                   "polyline":{"geoJsonLinestring":{"type":"LineString","coordinates":[[-74.1760,40.7410],[-74.1700,40.7380],[-74.1642,40.7347]]}},
                   "transitDetails":{
                     "stopDetails":{
                       "departureStop":{"name":"NJIT","location":{"latLng":{"latitude":40.7410,"longitude":-74.1760}}},
                       "departureTime":"2026-08-22T12:07:00Z",
                       "arrivalStop":{"name":"Newark Penn Station","location":{"latLng":{"latitude":40.7347,"longitude":-74.1642}}},
                       "arrivalTime":"2026-08-22T12:22:00Z"},
                     "headsign":"Newark Penn Station","stopCount": 5,
                     "transitLine":{"agencies":[{"name":"NJ TRANSIT"}],"name":"Newark Local","nameShort":"62","vehicle":{"name":{"text":"Bus"},"type":"BUS"}}}},
                  {"travelMode":"WALK","staticDuration":"120s","distanceMeters":120,
                   "polyline":{"geoJsonLinestring":{"type":"LineString","coordinates":[[-74.1642,40.7347],[-74.1630,40.7340]]}},
                   "startLocation":{"latLng":{"latitude":40.7347,"longitude":-74.1642}},
                   "endLocation":{"latLng":{"latitude":40.7340,"longitude":-74.1630}}}
                ]}]}]}""".formatted(fare);
    }
}
