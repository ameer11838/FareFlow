package com.fareflow.integration;

import com.fareflow.user.User;
import com.fareflow.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authentication mode. The property override is what makes this suite meaningful:
 * the rest of the integration tests run in demo mode, and these run with the same
 * code path a deployed instance would use.
 */
@TestPropertySource(properties = {
        "fareflow.auth.enabled=true",
        "fareflow.jwt.secret=integration-test-secret-that-is-long-enough-32!",
        "fareflow.jwt.expiration-seconds=3600",
})
class AuthEnabledIntegrationTest extends IntegrationTestBase {

    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;

    // ---------------- registration ----------------

    @Test
    @DisplayName("registration returns 201 with a token and the new user")
    void register() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ameer Hassan", "ameer@example.com", "correct-horse-battery", 5000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.expiresInSeconds", is(3600)))
                .andExpect(jsonPath("$.user.name", is("Ameer Hassan")))
                .andExpect(jsonPath("$.user.weeklyBudgetCents", is(5000)));
    }

    @Test
    @DisplayName("the password is stored only as a BCrypt hash")
    void passwordIsHashed() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ameer", "hash@example.com", "super-secret-password", 5000)))
                .andExpect(status().isCreated());

        User saved = users.findByEmailIgnoreCase("hash@example.com").orElseThrow();

        assertThat(saved.getPasswordHash()).isNotNull();
        assertThat(saved.getPasswordHash()).doesNotContain("super-secret-password");
        assertThat(saved.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches("super-secret-password", saved.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("the API never echoes a password back")
    void responseCarriesNoPassword() throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ameer", "echo@example.com", "super-secret-password", 5000)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("super-secret-password");
        assertThat(response).doesNotContain("passwordHash");
    }

    @Test
    @DisplayName("a duplicate email is rejected with 409")
    void duplicateEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body("A", "dupe@example.com", "password-one-two", 5000)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body("B", "dupe@example.com", "password-one-two", 5000)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a short password is rejected with a field error")
    void shortPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body("A", "short@example.com", "abc", 5000)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password", notNullValue()));
    }

    // ---------------- login ----------------

    @Test
    @DisplayName("login with correct credentials returns a token")
    void login() throws Exception {
        register("login@example.com", "the-right-password");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("email", "login@example.com", "password", "the-right-password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.user.email", is("login@example.com")));
    }

    @Test
    @DisplayName("a wrong password is 401 with a message that does not say which half failed")
    void wrongPassword() throws Exception {
        register("wrong@example.com", "the-right-password");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("email", "wrong@example.com", "password", "not-the-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail", is("Incorrect email or password")));
    }

    @Test
    @DisplayName("an unknown email produces the identical response, so accounts cannot be enumerated")
    void unknownEmailIsIndistinguishable() throws Exception {
        register("known@example.com", "the-right-password");

        String unknown = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "nobody@example.com", "password", "whatever-here"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String wrongPassword = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "known@example.com", "password", "whatever-here"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknown).isEqualTo(wrongPassword);
    }

    @Test
    @DisplayName("the seeded demo user cannot be logged into")
    void demoUserCannotAuthenticate() throws Exception {
        // It has no password hash at all, so there is nothing to guess.
        assertThat(demoUser().canAuthenticate()).isFalse();

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("email", "demo@fareflow.app", "password", "anything-at-all"))))
                .andExpect(status().isUnauthorized());
    }

    // ---------------- protected endpoints ----------------

    @Test
    @DisplayName("protected endpoints without a token are 401, not 403")
    void unauthenticatedIs401() throws Exception {
        for (String path : new String[]{"/api/trips", "/api/ledger", "/api/wallet",
                "/api/insights", "/api/dashboard", "/api/users/me", "/api/auth/me"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("a garbage token is still 401")
    void garbageTokenIs401() throws Exception {
        mockMvc.perform(get("/api/wallet").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a valid token unlocks the caller's own data")
    void validTokenWorks() throws Exception {
        String token = register("me@example.com", "the-right-password");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("me@example.com")));

        mockMvc.perform(get("/api/wallet").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalanceCents", is(5000)));
    }

    @Test
    @DisplayName("public endpoints stay reachable without a token")
    void publicEndpointsOpen() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/recommendations")
                        .param("origin", "Newark").param("destination", "Manhattan"))
                .andExpect(status().isOk());
    }

    // ---------------- isolation between users ----------------

    @Test
    @DisplayName("one user cannot see another user's trips or ledger")
    void usersAreIsolated() throws Exception {
        String alice = register("alice@example.com", "alice-password-ok");
        String bob = register("bob@example.com", "bob-password-okay");

        // Alice takes a trip.
        mockMvc.perform(post("/api/trips").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("routeId", 2, "selectedLabel", "BEST_VALUE"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/trips").header("Authorization", "Bearer " + alice))
                .andExpect(jsonPath("$.totalElements", is(1)));

        // Bob sees nothing of hers -- and there is no parameter he could change.
        mockMvc.perform(get("/api/trips").header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));

        mockMvc.perform(get("/api/ledger").header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));

        mockMvc.perform(get("/api/wallet").header("Authorization", "Bearer " + bob))
                .andExpect(jsonPath("$.spentThisWeekCents", is(0)));
    }

    @Test
    @DisplayName("fetching another user's trip by id is not possible")
    void cannotFetchAnotherUsersTripById() throws Exception {
        String alice = register("alice2@example.com", "alice-password-ok");
        String bob = register("bob2@example.com", "bob-password-okay");

        long tripId = objectMapper.readTree(
                        mockMvc.perform(post("/api/trips").header("Authorization", "Bearer " + alice)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(
                                                Map.of("routeId", 2, "selectedLabel", "BEST_VALUE"))))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString())
                .get("id").asLong();

        // 404 rather than 403: confirming the id exists would itself be a small leak.
        mockMvc.perform(get("/api/trips/{id}", tripId).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound());

        // And Bob certainly cannot cancel it, which would move Alice's money.
        mockMvc.perform(post("/api/trips/{id}/cancel", tripId).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/trips/{id}", tripId).header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("budget changes apply to the caller only")
    void budgetIsSelfScoped() throws Exception {
        String alice = register("alice3@example.com", "alice-password-ok");
        String bob = register("bob3@example.com", "bob-password-okay");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/users/me/budget").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("weeklyBudgetCents", 9999))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeklyBudgetCents", is(9999)));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + bob))
                .andExpect(jsonPath("$.weeklyBudgetCents", is(5000)));
    }

    @Test
    @DisplayName("the config endpoint reports auth mode")
    void configReportsAuthMode() throws Exception {
        mockMvc.perform(get("/api/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authEnabled", is(true)))
                .andExpect(jsonPath("$.demoMode", is(false)));
    }

    @Test
    @DisplayName("registering while auth is on does not expose a user list endpoint")
    void noUserListEndpoint() throws Exception {
        String token = register("listing@example.com", "the-right-password");
        // The old open /api/users listing is gone entirely.
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ---------------- helpers ----------------

    private String register(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Test User", email, password, 5000)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private String body(String name, String email, String password, long budget) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "name", name, "email", email, "password", password, "weeklyBudgetCents", budget));
    }
}
