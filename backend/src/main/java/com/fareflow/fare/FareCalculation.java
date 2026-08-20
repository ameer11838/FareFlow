package com.fareflow.fare;

import com.fareflow.common.Money;

import java.util.List;

/**
 * The priced result for one journey.
 *
 * <p>Integer cents throughout. {@link #totalFareCents()} is null — not zero — when
 * the journey could not be fully priced, so an unknown fare can never be mistaken
 * for a free ride.
 */
public record FareCalculation(
        Long totalFareCents,
        long baseFareCents,
        long transferAdjustmentCents,
        long capAdjustmentCents,
        long passAdjustmentCents,
        FareStatus status,
        FareSource source,
        List<FareLine> lines
) {

    public FareCalculation {
        lines = lines == null ? List.of() : List.copyOf(lines);
        if (status == FareStatus.UNKNOWN && totalFareCents != null) {
            throw new IllegalArgumentException("An UNKNOWN fare must not carry a total");
        }
        if (status != FareStatus.UNKNOWN && totalFareCents == null) {
            throw new IllegalArgumentException("A priced fare must carry a total");
        }
    }

    public boolean isPriced() {
        return totalFareCents != null;
    }

    /**
     * A receipt-style breakdown built from the same integers as the total, so the
     * explanation can never disagree with the amount charged.
     */
    public List<String> explanationLines() {
        return lines.stream()
                .map(line -> "%s  %s".formatted(line.label(),
                        line.amountCents() == 0 && line.type() == FareLine.FareLineType.UNPRICED
                                ? "not priced"
                                : Money.format(line.amountCents())))
                .toList();
    }

    public static FareCalculation unknown(List<FareLine> lines, String reason) {
        return new FareCalculation(null, 0, 0, 0, 0,
                FareStatus.UNKNOWN, FareSource.UNKNOWN, lines);
    }
}
