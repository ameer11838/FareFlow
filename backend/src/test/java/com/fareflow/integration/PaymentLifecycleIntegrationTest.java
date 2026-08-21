package com.fareflow.integration;

import com.fareflow.ledger.LedgerRepository;
import com.fareflow.payment.PaymentEventRepository;
import com.fareflow.payment.PaymentIntentRepository;
import com.fareflow.trip.TripRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies the route choice → payment → trip → ledger lifecycle end to end. */
class PaymentLifecycleIntegrationTest extends IntegrationTestBase {

    @Autowired private PaymentIntentRepository paymentRepository;
    @Autowired private PaymentEventRepository eventRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private LedgerRepository ledgerRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("FareEngine amount becomes a settled payment, trip, and linked ledger charge")
    void settlesAuthoritativeFare() throws Exception {
        String journeyId = pricedJourney("Newark", "Manhattan");

        String created = create(journeyId, "pay-authoritative", "FAREFLOW_WALLET", 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("CREATED")))
                .andExpect(jsonPath("$.amountCents", greaterThan(1)))
                .andExpect(jsonPath("$.trip").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(created).get("id").asText();
        long authoritativeFare = objectMapper.readTree(created).get("amountCents").asLong();

        mockMvc.perform(post("/api/payments/intents/{id}/confirm", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SETTLED")))
                .andExpect(jsonPath("$.trip.fareCents", is((int) authoritativeFare)))
                .andExpect(jsonPath("$.events.length()", is(4)));

        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(tripRepository.count()).isEqualTo(1);
        assertThat(ledgerRepository.count()).isEqualTo(1);
        var entry = ledgerRepository.findAll().getFirst();
        assertThat(entry.getAmountCents()).isEqualTo(-authoritativeFare);
        assertThat(entry.getPaymentIntentId().toString()).isEqualTo(id);

        mockMvc.perform(get("/api/payments/reconciliation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issueCount", is(0)))
                .andExpect(jsonPath("$.countsByStatus.SETTLED", is(1)));
    }

    @Test
    @DisplayName("a card decline creates no trip or charge and can be retried safely")
    void declineThenRetry() throws Exception {
        String journeyId = pricedJourney("Newark", "Manhattan");
        String created = create(journeyId, "pay-decline", "SIMULATED_CARD", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(post("/api/payments/intents/{id}/confirm", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"simulatedCardToken\":\"tok_simulated_decline\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.failureCode", is("CARD_DECLINED")))
                .andExpect(jsonPath("$.trip").doesNotExist());

        assertThat(tripRepository.count()).isZero();
        assertThat(ledgerRepository.count()).isZero();

        mockMvc.perform(post("/api/payments/intents/{id}/retry", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SETTLED")))
                .andExpect(jsonPath("$.attemptCount", is(2)));

        assertThat(tripRepository.count()).isEqualTo(1);
        assertThat(ledgerRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("idempotent create replays, but key reuse for another rail is rejected")
    void idempotencyFingerprint() throws Exception {
        String journeyId = pricedJourney("Newark", "Manhattan");
        String first = create(journeyId, "pay-replay", "FAREFLOW_WALLET", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = create(journeyId, "pay-replay", "FAREFLOW_WALLET", null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(first).get("id").asText())
                .isEqualTo(objectMapper.readTree(second).get("id").asText());
        assertThat(paymentRepository.count()).isEqualTo(1);

        create(journeyId, "pay-replay", "SIMULATED_CARD", null)
                .andExpect(status().isConflict());
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("refund is idempotent and preserves the original charge")
    void refundPreservesLedgerHistory() throws Exception {
        String journeyId = pricedJourney("Newark", "Manhattan");
        String created = create(journeyId, "pay-refund", "FAREFLOW_WALLET", null)
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).get("id").asText();
        mockMvc.perform(post("/api/payments/intents/{id}/confirm", id)
                .contentType(MediaType.APPLICATION_JSON).content("{}"));

        mockMvc.perform(post("/api/payments/intents/{id}/refund", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REFUNDED")))
                .andExpect(jsonPath("$.trip.status", is("CANCELLED")));
        mockMvc.perform(post("/api/payments/intents/{id}/refund", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REFUNDED")));

        var entries = ledgerRepository.findAll();
        assertThat(entries).hasSize(2);
        assertThat(entries.stream().mapToLong(entry -> entry.getAmountCents()).sum()).isZero();
        assertThat(eventRepository.findAll()).hasSize(5);
    }

    @Test
    @DisplayName("an unknown fare can be recorded, but can never become a zero-dollar payment")
    void unknownFareNeverCreatesPayment() throws Exception {
        String search = mockMvc.perform(get("/api/journeys")
                        .param("from", "Philadelphia").param("to", "Manhattan"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String unknownId = null;
        for (var option : objectMapper.readTree(search).get("options")) {
            if (option.get("fareCents").isNull()) {
                unknownId = option.get("journeyId").asText();
                break;
            }
        }
        assertThat(unknownId).isNotNull();

        mockMvc.perform(post("/api/payments/intents")
                        .header("Idempotency-Key", "unknown-payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "from", "Philadelphia", "to", "Manhattan",
                                "journeyId", unknownId, "confirmUnknownFare", true,
                                "paymentMethod", "FAREFLOW_WALLET"))))
                .andExpect(status().isConflict());

        assertThat(paymentRepository.count()).isZero();
        assertThat(ledgerRepository.count()).isZero();

        mockMvc.perform(post("/api/journeys/take")
                        .header("Idempotency-Key", "unknown-record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "from", "Philadelphia", "to", "Manhattan",
                                "journeyId", unknownId, "confirmUnknownFare", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fareCents", is(0)));

        assertThat(tripRepository.count()).isEqualTo(1);
        assertThat(paymentRepository.count()).isZero();
        assertThat(ledgerRepository.count()).isZero();
    }

    @Test
    @DisplayName("the database rejects edits to append-only financial history")
    void financialHistoryIsAppendOnly() throws Exception {
        String journeyId = pricedJourney("Newark", "Manhattan");
        String created = create(journeyId, "append-only", "FAREFLOW_WALLET", null)
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).get("id").asText();
        mockMvc.perform(post("/api/payments/intents/{id}/confirm", id)
                .contentType(MediaType.APPLICATION_JSON).content("{}"));

        long ledgerId = ledgerRepository.findAll().getFirst().getId();
        long eventId = eventRepository.findAll().getFirst().getId();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE ledger_entries SET description = 'tampered' WHERE id = ?", ledgerId))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM payment_events WHERE id = ?", eventId))
                .hasMessageContaining("append-only");
    }

    private String pricedJourney(String from, String to) throws Exception {
        String body = mockMvc.perform(get("/api/journeys").param("from", from).param("to", to))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (var option : objectMapper.readTree(body).get("options")) {
            if (!option.get("fareCents").isNull()) {
                return option.get("journeyId").asText();
            }
        }
        throw new AssertionError("No priced journey");
    }

    private org.springframework.test.web.servlet.ResultActions create(
            String journeyId, String key, String method, Integer fakeAmount) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("from", "Newark");
        body.put("to", "Manhattan");
        body.put("journeyId", journeyId);
        body.put("paymentMethod", method);
        body.put("confirmUnknownFare", false);
        if (fakeAmount != null) {
            body.put("amountCents", fakeAmount);
            body.put("fareCents", fakeAmount);
        }
        return mockMvc.perform(post("/api/payments/intents")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }
}
