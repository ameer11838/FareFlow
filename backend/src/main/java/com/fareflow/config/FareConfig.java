package com.fareflow.config;

import com.fareflow.fare.FareEngine;
import com.fareflow.fare.rules.DistanceBandFarePolicy;
import com.fareflow.fare.rules.FareCap;
import com.fareflow.fare.rules.FarePolicy;
import com.fareflow.fare.rules.FlatFarePolicy;
import com.fareflow.fare.rules.TransferRule;
import com.fareflow.fare.rules.UnpricedFarePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Published tariffs, transfer agreements, and fare caps.
 *
 * <p>Every figure here is a real published fare, in integer cents. Where a fare is
 * genuinely dynamic it gets an {@link UnpricedFarePolicy} rather than a plausible
 * guess — see the Amtrak entry.
 *
 * <p>Declared as configuration rather than constants inside the engine so a tariff
 * change is a data change, and so the engine itself stays free of agency names.
 */
@Configuration
public class FareConfig {

    @Bean
    public List<FarePolicy> farePolicies() {
        return List.of(
                // Flat fares: one price regardless of distance.
                new FlatFarePolicy("PATH_FLAT", "PATH", 300, "PATH base fare"),
                new FlatFarePolicy("MTA_FLAT", "MTA", 290, "NYC Subway base fare"),
                new FlatFarePolicy("NLR_FLAT", "NJ_TRANSIT", 160, "Newark Light Rail fare"),

                // Commuter rail priced by distance band. Approximating a zone tariff,
                // which is why journeys using these report as ESTIMATED.
                new DistanceBandFarePolicy("SEPTA_REGIONAL_RAIL", "SEPTA", List.of(
                        new DistanceBandFarePolicy.Band(10, 500, "SEPTA Regional Rail (Zone 1-2)"),
                        new DistanceBandFarePolicy.Band(30, 800, "SEPTA Regional Rail (Zone 3)"),
                        new DistanceBandFarePolicy.Band(100, 1000, "SEPTA Regional Rail (Zone 4 / Trenton)"))),

                new DistanceBandFarePolicy("NJT_ZONE_RAIL", "NJ_TRANSIT", List.of(
                        new DistanceBandFarePolicy.Band(15, 425, "NJ Transit rail (short hop)"),
                        new DistanceBandFarePolicy.Band(40, 800, "NJ Transit rail (mid corridor)"),
                        new DistanceBandFarePolicy.Band(200, 1760, "NJ Transit rail (Trenton - New York)"))),

                new DistanceBandFarePolicy("NJT_AIRPORT", "NJ_TRANSIT", List.of(
                        new DistanceBandFarePolicy.Band(200, 1750, "NJ Transit + AirTrain EWR"))),

                // Amtrak is yield-managed: the same seat ranges from roughly $30 to
                // $250 depending on demand and how far ahead it is booked. Any single
                // number FareFlow printed would be fiction, so it prices nothing and
                // the journey is reported as UNKNOWN.
                new UnpricedFarePolicy("AMTRAK_DYNAMIC", "AMTRAK",
                        "fare varies by demand and booking date"));
    }

    /**
     * Inter-agency transfer credits.
     *
     * <p>PATH to subway is the real one that matters here: riders using OMNY get a
     * free onward subway ride after a PATH trip, which is exactly the kind of rule
     * a fare-aware product should surface and a mapping app never will.
     */
    @Bean
    public List<TransferRule> transferRules() {
        return List.of(
                new TransferRule("PATH", "MTA", 290, "PATH → Subway transfer credit"),
                new TransferRule("MTA", "PATH", 290, "Subway → PATH transfer credit"),
                new TransferRule("NJ_TRANSIT", "PATH", 100, "NJ Transit → PATH connection discount"));
    }

    /**
     * Fare caps. Once the cap is reached, further rides on that agency are free.
     */
    @Bean
    public List<FareCap> fareCaps() {
        return List.of(
                new FareCap("MTA", FareCap.Period.WEEKLY, 3400, "NYC Subway weekly fare cap"));
    }

    @Bean
    public FareEngine fareEngine(List<FarePolicy> farePolicies,
                                 List<TransferRule> transferRules,
                                 List<FareCap> fareCaps) {
        return new FareEngine(farePolicies, transferRules, fareCaps);
    }
}
