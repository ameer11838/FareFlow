package com.fareflow.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FareFlow's proposed usage-pricing rules.
 *
 * <p>These are product simulation inputs, not agency tariffs. Keeping them in
 * configuration makes that distinction explicit and lets a future partner or
 * sandbox provider replace the numbers without replacing the payment domain.
 */
@ConfigurationProperties(prefix = "fareflow.usage-pricing")
public record UsageFareProperties(
        String version,
        ModeRule bus,
        ModeRule rail,
        ModeRule subway,
        ModeRule lightRail,
        ModeRule ferry
) {
    public record ModeRule(long baseCents, long centsPerMile, long centsPerStop) {
        public ModeRule {
            if (baseCents < 0 || centsPerMile < 0 || centsPerStop < 0) {
                throw new IllegalArgumentException("Usage-pricing values must not be negative");
            }
        }
    }

    public ModeRule forMode(String mode) {
        return switch (mode) {
            case "BUS" -> bus;
            case "RAIL" -> rail;
            case "SUBWAY" -> subway;
            case "LIGHT_RAIL" -> lightRail;
            case "FERRY" -> ferry;
            default -> throw new IllegalArgumentException(
                    "Usage pricing supports Bus, Train, Subway, and Ferry only");
        };
    }
}
