package com.fareflow.fare;

/** Where a fare figure came from, so a number can always be traced. */
public enum FareSource {
    /** Computed by FareFlow's rule engine from published tariffs. */
    FARE_RULE_ENGINE,
    /** Supplied directly by a routing provider. */
    PROVIDER,
    /** A fixed value carried on a seeded fixture row. */
    STATIC_RULE,
    /** No source could price this leg. */
    UNKNOWN
}
