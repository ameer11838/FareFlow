package com.fareflow.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Demo mode: no login, but also no way for a caller to name which user they are.
 * That second property is what makes it safe to leave running for a recruiter.
 */
class DemoModeIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("everything opens without a token")
    void noTokenRequired() throws Exception {
        for (String path : new String[]{"/api/auth/me", "/api/trips", "/api/ledger",
                "/api/wallet", "/api/insights", "/api/dashboard", "/api/users/me"}) {
            mockMvc.perform(get(path)).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("every request resolves to the one seeded demo identity")
    void resolvesToDemoUser() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Ameer Demo")))
                .andExpect(jsonPath("$.email", is("demo@fareflow.app")));
    }

    @Test
    @DisplayName("the config endpoint advertises demo mode and who the demo user is")
    void configReportsDemoMode() throws Exception {
        mockMvc.perform(get("/api/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authEnabled", is(false)))
                .andExpect(jsonPath("$.demoMode", is(true)))
                .andExpect(jsonPath("$.demoUserName", is("Ameer Demo")));
    }

    @Test
    @DisplayName("exactly one demo user exists, enforced by the database")
    void singleDemoUser() {
        assertThat(userRepository.findAll().stream().filter(u -> u.isDemo()).toList()).hasSize(1);
    }

    @Test
    @DisplayName("a caller cannot select a different user, even by guessing ids")
    void cannotImpersonate() throws Exception {
        // Another account exists...
        com.fareflow.user.User other = userRepository.save(new com.fareflow.user.User(
                "Someone Else", "other@example.com", 12345, "America/New_York"));

        // ...but there is no parameter, header, or body field that selects it.
        mockMvc.perform(get("/api/wallet").param("userId", String.valueOf(other.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeklyBudgetCents", is(5000)));

        mockMvc.perform(get("/api/auth/me").header("X-User-Id", String.valueOf(other.getId())))
                .andExpect(jsonPath("$.email", is("demo@fareflow.app")));

        // A forged bearer token is ignored too: demo mode never reads one.
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer forged." + other.getId()))
                .andExpect(jsonPath("$.email", is("demo@fareflow.app")));
    }

    @Test
    @DisplayName("registering is refused while the server is in demo mode")
    void registrationRefused() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "name", "Nope", "email", "nope@example.com",
                        "password", "password-one-two", "weeklyBudgetCents", 5000))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a trip taken in demo mode belongs to the demo user")
    void tripBelongsToDemoUser() throws Exception {
        mockMvc.perform(post("/api/trips").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("routeId", 2, "selectedLabel", "BEST_VALUE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId", is((int) demoUser().getId().longValue())));
    }
}
