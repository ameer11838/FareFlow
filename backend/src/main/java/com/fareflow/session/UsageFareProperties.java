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
        ModeRule express,
        ModeRule rail,
        ModeRule subway,
        ModeRule lightRail,
        ModeRule ferry,
        TransferRules transfers,
        FareCaps caps,
        RiderDiscounts riderDiscounts
) {
    public record ModeRule(long baseCents, long centsPerMile, long centsPerStop) {
        public ModeRule {
            if (baseCents < 0 || centsPerMile < 0 || centsPerStop < 0) {
                throw new IllegalArgumentException("Usage-pricing values must not be negative");
            }
        }
    }

    public record TransferRules(int sameOperatorCreditPercent,
                                int crossOperatorCreditPercent) {
        public TransferRules {
            requirePercent(sameOperatorCreditPercent, "Same-operator transfer credit");
            requirePercent(crossOperatorCreditPercent, "Cross-operator transfer credit");
        }
    }

    public record FareCaps(long dailyCents, long weeklyCents) {
        public FareCaps {
            if (dailyCents <= 0 || weeklyCents <= 0 || weeklyCents < dailyCents) {
                throw new IllegalArgumentException(
                        "Usage-pricing caps must be positive and weekly must not be below daily");
            }
        }
    }

    /** Percent of the standard fare paid by each eligible rider category. */
    public record RiderDiscounts(int studentPercent, int seniorPercent, int reducedPercent) {
        public RiderDiscounts {
            requirePercent(studentPercent, "Student fare");
            requirePercent(seniorPercent, "Senior fare");
            requirePercent(reducedPercent, "Reduced fare");
        }
    }

    public UsageFareProperties {
        express = express == null ? new ModeRule(150, 12, 5) : express;
        transfers = transfers == null ? new TransferRules(100, 50) : transfers;
        caps = caps == null ? new FareCaps(1_200, 6_000) : caps;
        riderDiscounts = riderDiscounts == null
                ? new RiderDiscounts(75, 50, 50) : riderDiscounts;
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

    /** Uses the higher express schedule only when the provider labels the service express. */
    public ModeRule forService(String mode, String providerLineName) {
        if ("BUS".equals(mode) && providerLineName != null
                && providerLineName.toLowerCase(java.util.Locale.ROOT).contains("express")) {
            return express;
        }
        return forMode(mode);
    }

    public int payablePercent(com.fareflow.profile.FareCategory category) {
        return switch (category) {
            case REGULAR -> 100;
            case STUDENT -> riderDiscounts.studentPercent();
            case SENIOR -> riderDiscounts.seniorPercent();
            case REDUCED -> riderDiscounts.reducedPercent();
        };
    }

    private static void requirePercent(int value, String label) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(label + " must be between 0 and 100 percent");
        }
    }
}
