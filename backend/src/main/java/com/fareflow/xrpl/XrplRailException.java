package com.fareflow.xrpl;

/**
 * The RLUSD rail could not complete something on the ledger.
 *
 * <p>Distinct from a fare or state error: nothing about the rider's trip is wrong,
 * a public network simply did not cooperate. Callers translate it into a failed
 * payment the rider can retry on another rail, never into a lost trip.
 */
public class XrplRailException extends RuntimeException {

    public XrplRailException(String message) {
        super(message);
    }

    public XrplRailException(String message, Throwable cause) {
        super(message, cause);
    }
}
