package com.fareflow.integration;

import com.fareflow.ledger.LedgerRepository;
import com.fareflow.payment.PaymentIntentRepository;
import com.fareflow.session.TransitSessionRepository;
import com.fareflow.trip.TripRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransitSessionIntegrationTest extends IntegrationTestBase {

    @Autowired private TransitSessionRepository sessionRepository;
    @Autowired private PaymentIntentRepository paymentRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private LedgerRepository ledgerRepository;

    @Test
    @DisplayName("ending before any transit progress creates an explicit no-charge session")
    void noBoardingMeansNoCharge() throws Exception {
        String id = start("no-board");

        mockMvc.perform(post("/api/transit-sessions/{id}/end", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("NO_CHARGE")))
                .andExpect(jsonPath("$.finalFareCents", is(0)))
                .andExpect(jsonPath("$.distanceTravelledMetres", is(0)));

        mockMvc.perform(post("/api/transit-sessions/{id}/pay", id)
                        .header("Idempotency-Key", "no-board-payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"FAREFLOW_WALLET\"}"))
                .andExpect(status().isConflict());

        assertThat(paymentRepository.count()).isZero();
        assertThat(tripRepository.count()).isZero();
        assertThat(ledgerRepository.count()).isZero();
    }

    @Test
    @DisplayName("recorded usage becomes one idempotent payment, trip, and ledger charge")
    void usageSessionSettlesExactlyOnce() throws Exception {
        String id = start("usage-trip");
        String advanced = mockMvc.perform(post("/api/transit-sessions/{id}/advance", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")))
                .andExpect(jsonPath("$.completedStops", is(1)))
                .andExpect(jsonPath("$.distanceTravelledMetres", greaterThan(0)))
                .andExpect(jsonPath("$.currentEstimatedFareCents", greaterThan(0)))
                .andExpect(jsonPath("$.currentFareCents", greaterThan(0)))
                .andExpect(jsonPath("$.stopFareProgress[1].state", is("CURRENT")))
                .andExpect(jsonPath("$.stopFareProgress[2].state", is("NEXT")))
                .andExpect(jsonPath("$.nextStopFareIncreaseCents", greaterThan(0)))
                .andReturn().getResponse().getContentAsString();
        long currentFare = objectMapper.readTree(advanced).get("currentEstimatedFareCents").asLong();

        mockMvc.perform(post("/api/transit-sessions/{id}/end", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.finalFareCents", is((int) currentFare)))
                .andExpect(jsonPath("$.canPay", is(true)));

        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("paymentMethod", "FAREFLOW_WALLET");
        // Unknown client money fields are ignored; the intent uses the session fare.
        payment.put("amountCents", 1);
        String first = mockMvc.perform(post("/api/transit-sessions/{id}/pay", id)
                        .header("Idempotency-Key", "usage-payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payment)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SETTLED")))
                .andExpect(jsonPath("$.amountCents", is((int) currentFare)))
                .andExpect(jsonPath("$.transitSessionId", is(id)))
                .andExpect(jsonPath("$.trip.fareModel", is("FAREFLOW_USAGE_V1")))
                .andExpect(jsonPath("$.trip.stopsTravelled", is(1)))
                .andReturn().getResponse().getContentAsString();

        String replay = mockMvc.perform(post("/api/transit-sessions/{id}/pay", id)
                        .header("Idempotency-Key", "usage-payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"FAREFLOW_WALLET\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(replay).get("id").asText())
                .isEqualTo(objectMapper.readTree(first).get("id").asText());
        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(tripRepository.count()).isEqualTo(1);
        assertThat(ledgerRepository.count()).isEqualTo(1);
        assertThat(ledgerRepository.findAll().getFirst().getAmountCents()).isEqualTo(-currentFare);
        assertThat(sessionRepository.findById(java.util.UUID.fromString(id)).orElseThrow()
                .getStatus().name()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("starting the same session request replays instead of duplicating")
    void startIsIdempotent() throws Exception {
        String first = start("session-replay");
        String second = start("session-replay");
        assertThat(second).isEqualTo(first);
        assertThat(sessionRepository.count()).isEqualTo(1);
    }

    private String start(String key) throws Exception {
        String search = mockMvc.perform(get("/api/journeys")
                        .param("from", "Newark").param("to", "Manhattan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0].usageFareMinCents", greaterThan(0)))
                .andExpect(jsonPath("$.options[0].usageFareMaxCents", greaterThan(0)))
                .andReturn().getResponse().getContentAsString();
        String journeyId = objectMapper.readTree(search).get("options").get(0)
                .get("journeyId").asText();
        String body = mockMvc.perform(post("/api/transit-sessions")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "from", "Newark", "to", "Manhattan",
                                "journeyId", journeyId, "profile", "BALANCED"))))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.status", is("STARTED")))
                .andExpect(jsonPath("$.currentFareCents", is(0)))
                .andExpect(jsonPath("$.currentEstimatedFareCents", is(0)))
                .andExpect(jsonPath("$.stopFareProgress[0].state", is("CURRENT")))
                .andExpect(jsonPath("$.stopFareProgress[1].state", is("NEXT")))
                .andExpect(jsonPath("$.nextStopFareIncreaseCents", greaterThan(0)))
                .andExpect(jsonPath("$.simulationNotice").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }
}
