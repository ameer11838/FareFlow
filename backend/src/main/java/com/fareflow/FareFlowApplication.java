package com.fareflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the FareFlow backend.
 *
 * <p>Component scanning starts from this package, so every feature package
 * ({@code route}, {@code recommendation}, ...) lives underneath {@code com.fareflow}.
 */
@SpringBootApplication
public class FareFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(FareFlowApplication.class, args);
    }
}
