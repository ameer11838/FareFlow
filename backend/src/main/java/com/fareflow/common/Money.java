package com.fareflow.common;

/**
 * Formatting helpers for monetary amounts held as integer cents.
 *
 * <p>Pure Java with no framework dependencies so the optimization package can
 * use it while remaining free of Spring and JPA.
 *
 * <p>There is deliberately no {@code double} anywhere in this class. Money is a
 * {@code long} count of cents from the database through to the JSON boundary;
 * only the display string is ever decimal.
 */
public final class Money {

    private Money() {
    }

    /**
     * Formats cents as a currency string: {@code 625 -> "$6.25"}, {@code -300 -> "-$3.00"}.
     */
    public static String format(long cents) {
        long absolute = Math.abs(cents);
        String formatted = "$%d.%02d".formatted(absolute / 100, absolute % 100);
        return cents < 0 ? "-" + formatted : formatted;
    }

    /**
     * Formats a per-minute rate given a total in cents and a number of minutes.
     * Rounds to the nearest cent.
     *
     * @throws IllegalArgumentException if {@code minutes} is zero
     */
    public static String formatPerMinute(long cents, int minutes) {
        if (minutes == 0) {
            throw new IllegalArgumentException("Cannot compute a per-minute rate over zero minutes");
        }
        long centsPerMinute = Math.round((double) cents / minutes);
        return format(centsPerMinute);
    }

    /**
     * Formats a minute count with correct pluralisation: {@code 1 -> "1 minute"}.
     */
    public static String formatMinutes(int minutes) {
        return Math.abs(minutes) == 1 ? minutes + " minute" : minutes + " minutes";
    }
}
