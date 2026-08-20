package com.fareflow.ledger;

/**
 * Kinds of money movement.
 *
 * <p>Each type constrains the sign of {@code amount_cents}, and that constraint is
 * enforced by the database as well as here.
 */
public enum LedgerEntryType {

    /** A user took a trip. Always negative — money out. */
    TRIP_CHARGE,

    /** A trip was cancelled. Always positive — money back. */
    REFUND,

    /** A correction, surcharge, or promotion. May be either sign, never zero. */
    FARE_ADJUSTMENT
}
