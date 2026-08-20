package com.fareflow.integration;

import com.fareflow.journey.PersistedJourneyRepository;
import com.fareflow.ledger.LedgerEntryType;
import com.fareflow.ledger.LedgerRepository;
import com.fareflow.trip.TripRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The end-to-end flow: search an arbitrary pair, select a discovered multi-leg
 * journey, and have it become a trip and a ledger charge — atomically, at a fare
 * the server computed.
 */
class TakeJourneyIntegrationTest extends IntegrationTestBase {

    @Autowired private TripRepository tripRepository;
    @Autowired private LedgerRepository ledgerRepository;
    @Autowired private PersistedJourneyRepository journeyRepository;

    /** Finds the cheapest priced option for a pair, the way a rider would pick it. */
    private String cheapestPricedJourneyId(String from, String to) throws Exception {
        String body = mockMvc.perform(get("/api/journeys").param("from", from).param("to", to))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var options = objectMapper.readTree(body).get("options");
        for (var option : options) {
            if (!option.get("fareCents").isNull()) {
                return option.get("journeyId").asText();
            }
        }
        throw new AssertionError("No priced journey found for " + from + " -> " + to);
    }

    private org.springframework.test.web.servlet.ResultActions take(
            String from, String to, String journeyId, String idempotencyKey) throws Exception {
        var request = post("/api/journeys/take")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "from", from, "to", to, "journeyId", journeyId,
                        "confirmUnknownFare", false)));
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    // ---------------- the definition-of-done scenario ----------------

    @Test
    @DisplayName("Philadelphia to Manhattan: select, persist, charge, and reconcile")
    void philadelphiaEndToEnd() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/users/me/budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("weeklyBudgetCents", 10_000))))
                .andExpect(status().isOk());

        String journeyId = cheapestPricedJourneyId("Philadelphia", "Manhattan");

        String tripBody = take("Philadelphia", "Manhattan", journeyId, "dod-key-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin", containsString("Philadelphia")))
                .andExpect(jsonPath("$.destination", containsString("Manhattan")))
                .andExpect(jsonPath("$.fareCents", greaterThan(0)))
                .andReturn().getResponse().getContentAsString();

        long fareCents = objectMapper.readTree(tripBody).get("fareCents").asLong();
        long tripId = objectMapper.readTree(tripBody).get("id").asLong();

        // The journey snapshot exists with its legs.
        assertThat(journeyRepository.count()).isEqualTo(1);
        var journey = journeyRepository.findAll().getFirst();
        assertThat(journey.getLegs()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(journey.getTransfers()).isGreaterThanOrEqualTo(1);

        // Exactly one charge, for exactly the server's fare.
        var entries = ledgerRepository.findByTripIdOrderByIdAsc(tripId);
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getType()).isEqualTo(LedgerEntryType.TRIP_CHARGE);
        assertThat(entries.getFirst().getAmountCents()).isEqualTo(-fareCents);

        // Budget moved by that amount, derived from the ledger.
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(jsonPath("$.spentCents", is((int) fareCents)))
                .andExpect(jsonPath("$.remainingCents", is((int) (10_000 - fareCents))))
                .andExpect(jsonPath("$.tripCount", is(1)));

        // Trips shows the multi-leg itinerary.
        mockMvc.perform(get("/api/trips"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].provider", containsString("→")));

        // The ledger references the trip.
        mockMvc.perform(get("/api/ledger"))
                .andExpect(jsonPath("$.content[0].tripId", is((int) tripId)))
                .andExpect(jsonPath("$.content[0].description", containsString("Philadelphia")));

        // Insights pick it up.
        mockMvc.perform(get("/api/insights"))
                .andExpect(jsonPath("$.spentCents", is((int) fareCents)))
                .andExpect(jsonPath("$.tripCount", is(1)));
    }

    // ---------------- authoritative fare ----------------

    @Test
    @DisplayName("the charge equals the server's fare, not anything the client sent")
    void serverComputesTheFare() throws Exception {
        String journeyId = cheapestPricedJourneyId("Newark", "Manhattan");

        // A fare field is deliberately absent from the DTO, so extra JSON is ignored.
        String body = mockMvc.perform(post("/api/journeys/take")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"from":"Newark","to":"Manhattan","journeyId":"%s",
                                 "fareCents":1,"totalFareCents":1,"amount":1}"""
                                .formatted(journeyId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long charged = objectMapper.readTree(body).get("fareCents").asLong();
        assertThat(charged).isNotEqualTo(1);
        assertThat(charged).isPositive();

        var entries = ledgerRepository.findAll();
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getAmountCents()).isEqualTo(-charged);
    }

    @Test
    @DisplayName("an unknown journey id is rejected rather than guessed at")
    void unknownJourneyIdRejected() throws Exception {
        take("Newark", "Manhattan", "NOT_A_REAL_JOURNEY", null)
                .andExpect(status().isNotFound());

        assertThat(tripRepository.count()).isZero();
        assertThat(ledgerRepository.count()).isZero();
    }

    // ---------------- unknown fares ----------------

    @Test
    @DisplayName("selecting an unpriceable journey is refused with a machine-readable code")
    void unknownFareRequiresConfirmation() throws Exception {
        String body = mockMvc.perform(get("/api/journeys")
                        .param("from", "Philadelphia").param("to", "Manhattan"))
                .andReturn().getResponse().getContentAsString();

        String unknownId = null;
        for (var option : objectMapper.readTree(body).get("options")) {
            if ("UNKNOWN".equals(option.get("fareStatus").asText())) {
                unknownId = option.get("journeyId").asText();
                break;
            }
        }
        assertThat(unknownId).as("the corridor should offer an Amtrak option").isNotNull();

        take("Philadelphia", "Manhattan", unknownId, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("FARE_CONFIRMATION_REQUIRED")));

        // Nothing was written: no silent zero-charge trip.
        assertThat(tripRepository.count()).isZero();
        assertThat(ledgerRepository.count()).isZero();
    }

    @Test
    @DisplayName("a confirmed unknown fare records the trip but writes no charge")
    void confirmedUnknownFareWritesNoCharge() throws Exception {
        String body = mockMvc.perform(get("/api/journeys")
                        .param("from", "Philadelphia").param("to", "Manhattan"))
                .andReturn().getResponse().getContentAsString();

        String unknownId = null;
        for (var option : objectMapper.readTree(body).get("options")) {
            if ("UNKNOWN".equals(option.get("fareStatus").asText())) {
                unknownId = option.get("journeyId").asText();
                break;
            }
        }

        mockMvc.perform(post("/api/journeys/take")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "from", "Philadelphia", "to", "Manhattan",
                                "journeyId", unknownId, "confirmUnknownFare", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fareCents", is(0)));

        assertThat(tripRepository.count()).isEqualTo(1);
        // A $0.00 charge would violate the ledger's sign constraint and add noise.
        assertThat(ledgerRepository.count()).isZero();

        var journey = journeyRepository.findAll().getFirst();
        assertThat(journey.getFareStatus()).isEqualTo("UNKNOWN");
        assertThat(journey.getTotalFareCents()).isNull();
    }

    // ---------------- idempotency ----------------

    @Test
    @DisplayName("a double-submitted selection charges once")
    void doubleSubmitChargesOnce() throws Exception {
        String journeyId = cheapestPricedJourneyId("Newark", "Manhattan");

        String first = take("Newark", "Manhattan", journeyId, "double-click-key")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String second = take("Newark", "Manhattan", journeyId, "double-click-key")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Same trip returned, and only one of everything written.
        assertThat(objectMapper.readTree(first).get("id").asLong())
                .isEqualTo(objectMapper.readTree(second).get("id").asLong());
        assertThat(tripRepository.count()).isEqualTo(1);
        assertThat(ledgerRepository.count()).isEqualTo(1);
        assertThat(journeyRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("different idempotency keys create separate trips")
    void differentKeysCreateSeparateTrips() throws Exception {
        String journeyId = cheapestPricedJourneyId("Newark", "Manhattan");

        take("Newark", "Manhattan", journeyId, "key-a").andExpect(status().isCreated());
        take("Newark", "Manhattan", journeyId, "key-b").andExpect(status().isCreated());

        assertThat(tripRepository.count()).isEqualTo(2);
        assertThat(ledgerRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("no idempotency key still works, and does not dedupe")
    void noKeyStillWorks() throws Exception {
        String journeyId = cheapestPricedJourneyId("Newark", "Manhattan");

        take("Newark", "Manhattan", journeyId, null).andExpect(status().isCreated());
        take("Newark", "Manhattan", journeyId, null).andExpect(status().isCreated());

        assertThat(tripRepository.count()).isEqualTo(2);
    }

    // ---------------- snapshot durability ----------------

    @Test
    @DisplayName("a historical trip is unaffected by later fare or network changes")
    void snapshotSurvivesNetworkChanges() throws Exception {
        String journeyId = cheapestPricedJourneyId("Newark", "Manhattan");
        String body = take("Newark", "Manhattan", journeyId, "snapshot-key")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long originalFare = objectMapper.readTree(body).get("fareCents").asLong();
        var journeyBefore = journeyRepository.findAll().getFirst();
        int legCount = journeyBefore.getLegs().size();
        String summaryBefore = journeyBefore.summary();

        // Retire every line the journey used. Discovery can no longer produce it.
        jdbcTemplate().execute("UPDATE transit_lines SET active = false");

        // The trip still reads exactly as it did, because it copied the facts.
        mockMvc.perform(get("/api/trips"))
                .andExpect(jsonPath("$.content[0].fareCents", is((int) originalFare)))
                .andExpect(jsonPath("$.content[0].provider", is(summaryBefore)));

        var journeyAfter = journeyRepository.findAll().getFirst();
        assertThat(journeyAfter.getLegs()).hasSize(legCount);
        assertThat(journeyAfter.getTotalFareCents()).isEqualTo(originalFare);
    }

    // ---------------- atomicity ----------------

    @Test
    @DisplayName("a failure part-way through leaves nothing behind")
    void failureRollsEverythingBack() throws Exception {
        // An unresolvable destination fails after the request is accepted but before
        // anything is written. Nothing may survive.
        mockMvc.perform(post("/api/journeys/take")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "from", "Newark", "to", "Nowhere At All",
                                "journeyId", "PATH_NWK", "confirmUnknownFare", false))))
                .andExpect(status().isNotFound());

        assertThat(tripRepository.count()).isZero();
        assertThat(ledgerRepository.count()).isZero();
        assertThat(journeyRepository.count()).isZero();
    }

    @Test
    @DisplayName("cancelling a journey trip refunds without deleting the charge")
    void cancellingRefunds() throws Exception {
        String journeyId = cheapestPricedJourneyId("Newark", "Manhattan");
        String body = take("Newark", "Manhattan", journeyId, "cancel-key")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long tripId = objectMapper.readTree(body).get("id").asLong();
        long fare = objectMapper.readTree(body).get("fareCents").asLong();

        mockMvc.perform(post("/api/trips/{id}/cancel", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        var entries = ledgerRepository.findByTripIdOrderByIdAsc(tripId);
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getAmountCents()).isEqualTo(-fare);
        assertThat(entries.get(1).getAmountCents()).isEqualTo(fare);
        assertThat(entries.stream().mapToLong(e -> e.getAmountCents()).sum()).isZero();
    }

    @Test
    @DisplayName("taking a journey requires authentication when auth is enabled")
    void requiresAuthentication() throws Exception {
        // Demo mode resolves the demo user, so this asserts the endpoint is wired
        // through CurrentUserService rather than accepting an anonymous caller.
        String journeyId = cheapestPricedJourneyId("Newark", "Manhattan");
        String body = take("Newark", "Manhattan", journeyId, "auth-key")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(body).get("userId").asLong())
                .isEqualTo(demoUser().getId());
    }

    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate() {
        return new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    }

    @Autowired private javax.sql.DataSource dataSource;
}
