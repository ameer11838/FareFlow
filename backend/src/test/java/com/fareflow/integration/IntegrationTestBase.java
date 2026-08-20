package com.fareflow.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fareflow.user.User;
import com.fareflow.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Base for tests that exercise the real Spring context against a real PostgreSQL
 * database.
 *
 * <p>Flyway is cleaned and re-migrated before each test so every test starts from
 * the same known state, including the seeded transit routes. Slower than mocking,
 * but it verifies the thing that actually matters here: that migrations, JPA
 * mappings, and database constraints all agree with each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private Flyway flyway;

    @Autowired
    protected UserRepository userRepository;

    /** The seeded identity every demo-mode request resolves to. */
    protected User demoUser() {
        return userRepository.findByEmailIgnoreCase("demo@fareflow.app").orElseThrow();
    }

    @BeforeEach
    void resetDatabase() {
        flyway.clean();
        flyway.migrate();
    }
}
