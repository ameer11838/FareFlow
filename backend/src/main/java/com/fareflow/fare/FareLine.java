package com.fareflow.fare;

/**
 * One line in a fare breakdown, in the shape a receipt would print.
 *
 * <p>Amounts are signed integer cents: positive adds to the fare, negative is a
 * credit. Summing every line gives the total, which is what makes the explanation
 * verifiable rather than decorative.
 */
public record FareLine(String label, long amountCents, FareLineType type) {

    public enum FareLineType {
        BASE_FARE,
        TRANSFER_CREDIT,
        CAP_ADJUSTMENT,
        PASS_ADJUSTMENT,
        DISCOUNT,
        UNPRICED
    }

    public static FareLine base(String label, long cents) {
        return new FareLine(label, cents, FareLineType.BASE_FARE);
    }

    public static FareLine transferCredit(String label, long cents) {
        return new FareLine(label, -Math.abs(cents), FareLineType.TRANSFER_CREDIT);
    }

    public static FareLine capAdjustment(String label, long cents) {
        return new FareLine(label, -Math.abs(cents), FareLineType.CAP_ADJUSTMENT);
    }

    public static FareLine passAdjustment(String label, long cents) {
        return new FareLine(label, -Math.abs(cents), FareLineType.PASS_ADJUSTMENT);
    }

    /** A leg nobody could price. Contributes zero, and forces the status to UNKNOWN. */
    public static FareLine unpriced(String label) {
        return new FareLine(label, 0, FareLineType.UNPRICED);
    }
}
