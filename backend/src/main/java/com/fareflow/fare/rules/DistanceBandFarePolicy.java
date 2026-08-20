package com.fareflow.fare.rules;

import com.fareflow.journey.JourneyLeg;

import java.util.List;
import java.util.Optional;

/**
 * Fare by distance band — the shape commuter rail actually uses.
 *
 * <p>Real zone tariffs key off station pairs rather than kilometres. Bands are a
 * deliberate approximation of that, which is exactly why journeys priced this way
 * are reported as {@code ESTIMATED} rather than {@code EXACT}: the model is
 * honest about being a model.
 */
public final class DistanceBandFarePolicy implements FarePolicy {

    /** @param upToKilometres inclusive upper bound of the band */
    public record Band(double upToKilometres, long fareCents, String label) {
    }

    private final String code;
    private final String agency;
    private final List<Band> bands;

    public DistanceBandFarePolicy(String code, String agency, List<Band> bands) {
        if (bands == null || bands.isEmpty()) {
            throw new IllegalArgumentException("At least one band is required");
        }
        this.code = code;
        this.agency = agency;
        this.bands = bands.stream()
                .sorted(java.util.Comparator.comparingDouble(Band::upToKilometres))
                .toList();
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String agency() {
        return agency;
    }

    @Override
    public Optional<Long> baseFareCents(JourneyLeg leg) {
        double kilometres = leg.distanceMetres() / 1000.0;
        return bands.stream()
                .filter(band -> kilometres <= band.upToKilometres())
                .findFirst()
                .map(Band::fareCents)
                // Beyond the last band, the furthest band applies rather than nothing:
                // a long ride costs at least the most expensive published band.
                .or(() -> Optional.of(bands.getLast().fareCents()));
    }

    @Override
    public String describe(JourneyLeg leg) {
        double kilometres = leg.distanceMetres() / 1000.0;
        return bands.stream()
                .filter(band -> kilometres <= band.upToKilometres())
                .findFirst()
                .map(Band::label)
                .orElse(bands.getLast().label());
    }
}
