package com.riderspay.autodriver;

/** Pure Java, deterministic money math used by the native Android UI. */
public final class FareEngine {
    private FareEngine() { }

    /** All rate inputs are stored in paise, not floating-point rupees. */
    public static long calculatePaise(long basePaise, long perKmPaise, long minimumPaise, double distanceMeters) {
        if (basePaise < 0 || perKmPaise < 0 || minimumPaise < 0) {
            throw new IllegalArgumentException("Fare values must be non-negative");
        }
        if (!Double.isFinite(distanceMeters) || distanceMeters < 0) {
            throw new IllegalArgumentException("Distance must be a non-negative finite number");
        }
        // Integer paise avoids display/rounding drift in fare estimates.
        long travelPaise = Math.round(perKmPaise * (distanceMeters / 1000.0));
        return Math.max(minimumPaise, Math.addExact(basePaise, travelPaise));
    }

    public static String currency(long paise) {
        return "₹" + String.format(java.util.Locale.US, "%,.2f", paise / 100.0);
    }
}
