package com.fareflow.health;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain JUnit -- no Spring context, no database. Keeps `mvn test` green even
 * when PostgreSQL is not running.
 */
class HealthControllerTest {

    @Test
    void returnsUpWith200() {
        var response = new HealthController().health();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("UP");
    }
}
