package com.fareflow.health;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness check used to confirm the application started and is serving HTTP.
 *
 * <p>Intentionally contains no business logic and touches no database. If this
 * responds, the web layer is up; whether the database is reachable is proven
 * separately by Flyway running at startup.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(HealthResponse.up());
    }
}
