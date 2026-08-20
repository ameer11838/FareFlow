package com.fareflow.fare;

import com.fareflow.fare.rules.*;
import com.fareflow.journey.Journey;
import com.fareflow.journey.JourneyLeg;
import com.fareflow.journey.TransitMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fare engine is the financial core, so it gets the same treatment the scoring
 * engine does: plain JUnit, no Spring, no database.
 */
class FareEngineTest {

    private static final Map<String, String> POLICIES = Map.of(
            "PATH_NWK", "PATH_FLAT",
            "NYCT_1", "MTA_FLAT",
            "NYCT_ACE", "MTA_FLAT",
            "AMTRAK_NER", "AMTRAK_DYNAMIC",
            "NJT_NEC", "NJT_ZONE_RAIL");

    private static FareEngine engine() {
        return new FareEngine(
                List.of(
                        new FlatFarePolicy("PATH_FLAT", "PATH", 300, "PATH base fare"),
                        new FlatFarePolicy("MTA_FLAT", "MTA", 290, "NYC Subway base fare"),
                        new DistanceBandFarePolicy("NJT_ZONE_RAIL", "NJ_TRANSIT", List.of(
                                new DistanceBandFarePolicy.Band(200, 1760, "NJ Transit rail"))),
                        new UnpricedFarePolicy("AMTRAK_DYNAMIC", "AMTRAK", "fare varies by demand")),
                List.of(new TransferRule("PATH", "MTA", 290, "PATH → Subway transfer credit")),
                List.of(new FareCap("MTA", FareCap.Period.WEEKLY, 3400, "NYC Subway weekly cap")));
    }

    private static JourneyLeg ride(String lineCode, String agency, String name,
                                   TransitMode mode, int minutes, double metres) {
        return new JourneyLeg(mode, agency, lineCode, name,
                "A", "Start", "B", "End", minutes, 4, metres, List.of());
    }

    private static Journey journeyOf(JourneyLeg... legs) {
        return new Journey("test", "Origin", "Destination", List.of(legs),
                Journey.DataSource.CURATED_NETWORK);
    }

    @Test
    @DisplayName("a single flat-fare leg is priced exactly")
    void singleFlatLeg() {
        FareCalculation fare = engine().price(
                journeyOf(ride("PATH_NWK", "PATH", "PATH", TransitMode.SUBWAY, 25, 14_000)),
                UserFareContext.anonymous(), POLICIES);

        assertThat(fare.totalFareCents()).isEqualTo(300);
        assertThat(fare.status()).isEqualTo(FareStatus.EXACT);
        assertThat(fare.source()).isEqualTo(FareSource.FARE_RULE_ENGINE);
    }

    @Test
    @DisplayName("walking legs are free and do not appear as fare lines")
    void walkingIsFree() {
        FareCalculation fare = engine().price(
                journeyOf(
                        JourneyLeg.walk("Home", "Station", 8, 640, List.of()),
                        ride("PATH_NWK", "PATH", "PATH", TransitMode.SUBWAY, 25, 14_000),
                        JourneyLeg.walk("Station", "Office", 5, 400, List.of())),
                UserFareContext.anonymous(), POLICIES);

        assertThat(fare.totalFareCents()).isEqualTo(300);
        assertThat(fare.lines()).hasSize(1);
    }

    @Test
    @DisplayName("a PATH to subway transfer credit zeroes the second fare")
    void transferCredit() {
        FareCalculation fare = engine().price(
                journeyOf(
                        ride("PATH_NWK", "PATH", "PATH", TransitMode.SUBWAY, 25, 14_000),
                        ride("NYCT_1", "MTA", "Subway", TransitMode.SUBWAY, 12, 5_000)),
                UserFareContext.anonymous(), POLICIES);

        // $3.00 + $2.90 - $2.90 credit = $3.00
        assertThat(fare.baseFareCents()).isEqualTo(590);
        assertThat(fare.transferAdjustmentCents()).isEqualTo(-290);
        assertThat(fare.totalFareCents()).isEqualTo(300);
        assertThat(fare.explanationLines())
                .anyMatch(line -> line.contains("PATH → Subway transfer credit"));
    }

    @Test
    @DisplayName("the breakdown always sums to the total")
    void breakdownReconciles() {
        FareCalculation fare = engine().price(
                journeyOf(
                        ride("PATH_NWK", "PATH", "PATH", TransitMode.SUBWAY, 25, 14_000),
                        ride("NYCT_1", "MTA", "Subway", TransitMode.SUBWAY, 12, 5_000)),
                UserFareContext.anonymous(), POLICIES);

        long sum = fare.lines().stream().mapToLong(FareLine::amountCents).sum();
        assertThat(sum).isEqualTo(fare.totalFareCents());
    }

    @Test
    @DisplayName("a transfer credit can never exceed the fare it applies to")
    void creditCannotCreateAPayout() {
        FareEngine generous = new FareEngine(
                List.of(new FlatFarePolicy("PATH_FLAT", "PATH", 300, "PATH"),
                        new FlatFarePolicy("MTA_FLAT", "MTA", 100, "Subway")),
                // A credit larger than the second leg's fare.
                List.of(new TransferRule("PATH", "MTA", 900, "Oversized credit")),
                List.of());

        FareCalculation fare = generous.price(
                journeyOf(
                        ride("PATH_NWK", "PATH", "PATH", TransitMode.SUBWAY, 25, 14_000),
                        ride("NYCT_1", "MTA", "Subway", TransitMode.SUBWAY, 12, 5_000)),
                UserFareContext.anonymous(), POLICIES);

        // Credit is clamped to $1.00, so the total is $3.00 -- not a negative fare.
        assertThat(fare.totalFareCents()).isEqualTo(300);
    }

    @Test
    @DisplayName("a weekly cap reduces the charge once the rider is near it")
    void weeklyCapApplies() {
        // $32.00 already spent against a $34.00 cap: only $2.00 is chargeable.
        UserFareContext context = new UserFareContext(3_200, 0, Set.of());

        FareCalculation fare = engine().price(
                journeyOf(ride("NYCT_1", "MTA", "Subway", TransitMode.SUBWAY, 12, 5_000)),
                context, POLICIES);

        assertThat(fare.totalFareCents()).isEqualTo(200);
        assertThat(fare.capAdjustmentCents()).isEqualTo(-90);
        assertThat(fare.explanationLines()).anyMatch(line -> line.contains("weekly cap"));
    }

    @Test
    @DisplayName("a rider past the cap travels free")
    void pastTheCapIsFree() {
        FareCalculation fare = engine().price(
                journeyOf(ride("NYCT_1", "MTA", "Subway", TransitMode.SUBWAY, 12, 5_000)),
                new UserFareContext(3_400, 0, Set.of()), POLICIES);

        assertThat(fare.totalFareCents()).isZero();
    }

    @Test
    @DisplayName("an active pass zeroes that agency's legs")
    void passZeroesTheFare() {
        FareCalculation fare = engine().price(
                journeyOf(ride("PATH_NWK", "PATH", "PATH", TransitMode.SUBWAY, 25, 14_000)),
                new UserFareContext(0, 0, Set.of("PATH")), POLICIES);

        assertThat(fare.totalFareCents()).isZero();
        assertThat(fare.explanationLines()).anyMatch(line -> line.contains("PATH pass"));
    }

    @Test
    @DisplayName("a dynamically priced leg makes the whole journey UNKNOWN, never zero")
    void dynamicPricingIsUnknown() {
        FareCalculation fare = engine().price(
                journeyOf(ride("AMTRAK_NER", "AMTRAK", "Amtrak", TransitMode.RAIL, 85, 130_000)),
                UserFareContext.anonymous(), POLICIES);

        assertThat(fare.status()).isEqualTo(FareStatus.UNKNOWN);
        assertThat(fare.totalFareCents()).isNull();
        assertThat(fare.isPriced()).isFalse();
        assertThat(fare.explanationLines()).anyMatch(line -> line.contains("not priced"));
    }

    @Test
    @DisplayName("one unpriceable leg makes the whole journey unknown, not partially priced")
    void partialPricingIsRefused() {
        // Reporting only the PATH leg would understate a journey that also includes Amtrak.
        FareCalculation fare = engine().price(
                journeyOf(
                        ride("AMTRAK_NER", "AMTRAK", "Amtrak", TransitMode.RAIL, 85, 130_000),
                        ride("PATH_NWK", "PATH", "PATH", TransitMode.SUBWAY, 25, 14_000)),
                UserFareContext.anonymous(), POLICIES);

        assertThat(fare.totalFareCents()).isNull();
        assertThat(fare.status()).isEqualTo(FareStatus.UNKNOWN);
    }

    @Test
    @DisplayName("distance-band pricing reports ESTIMATED rather than EXACT")
    void distanceBandsAreEstimates() {
        FareCalculation fare = engine().price(
                journeyOf(ride("NJT_NEC", "NJ_TRANSIT", "NJ Transit", TransitMode.RAIL, 70, 90_000)),
                UserFareContext.anonymous(), POLICIES);

        assertThat(fare.totalFareCents()).isEqualTo(1760);
        assertThat(fare.status()).isEqualTo(FareStatus.ESTIMATED);
    }

    @Test
    @DisplayName("a leg with no policy at all is unknown rather than free")
    void missingPolicyIsUnknown() {
        FareCalculation fare = engine().price(
                journeyOf(ride("MYSTERY_LINE", "UNKNOWN_AGENCY", "Mystery", TransitMode.BUS, 20, 8_000)),
                UserFareContext.anonymous(), POLICIES);

        assertThat(fare.totalFareCents()).isNull();
        assertThat(fare.status()).isEqualTo(FareStatus.UNKNOWN);
    }

    @Test
    @DisplayName("an agency's cap only discounts that agency's share of the journey")
    void capIsScopedToItsOwnAgency() {
        // $32.00 already spent against MTA's $34.00 weekly cap, so only $2.00 of
        // subway fare is chargeable -- but the NJ Transit leg must be untouched.
        UserFareContext context = new UserFareContext(3_200, 0, Set.of());

        FareCalculation fare = engine().price(
                journeyOf(
                        ride("NJT_NEC", "NJ_TRANSIT", "NJ Transit", TransitMode.RAIL, 70, 90_000),
                        ride("NYCT_1", "MTA", "Subway", TransitMode.SUBWAY, 12, 5_000)),
                context, POLICIES);

        // $17.60 rail (uncapped) + $2.90 subway - $0.90 cap reduction = $19.60.
        // Capping the journey total instead would have charged just $2.00.
        assertThat(fare.totalFareCents()).isEqualTo(1_960);
        assertThat(fare.capAdjustmentCents()).isEqualTo(-90);
    }

    @Test
    @DisplayName("a cap does nothing when the journey does not use that agency")
    void capIgnoresUnrelatedJourneys() {
        FareCalculation fare = engine().price(
                journeyOf(ride("NJT_NEC", "NJ_TRANSIT", "NJ Transit", TransitMode.RAIL, 70, 90_000)),
                new UserFareContext(9_999, 0, Set.of()), POLICIES);

        assertThat(fare.totalFareCents()).isEqualTo(1_760);
        assertThat(fare.capAdjustmentCents()).isZero();
    }

    @Test
    @DisplayName("a fare is never negative")
    void neverNegative() {
        FareCalculation fare = engine().price(
                journeyOf(ride("NYCT_1", "MTA", "Subway", TransitMode.SUBWAY, 12, 5_000)),
                new UserFareContext(99_999, 0, Set.of()), POLICIES);

        assertThat(fare.totalFareCents()).isGreaterThanOrEqualTo(0);
    }
}
