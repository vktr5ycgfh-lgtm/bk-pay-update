import com.riderspay.autodriver.FareEngine;
import com.riderspay.autodriver.GeoMath;

public final class FareEngineTest {
    private static void eq(long expected, long actual) {
        if (expected != actual) throw new AssertionError("expected " + expected + " got " + actual);
    }
    public static void main(String[] args) {
        eq(4000L, FareEngine.calculatePaise(4000, 1500, 3000, 0));
        eq(7000L, FareEngine.calculatePaise(4000, 1500, 3000, 2000));
        eq(5000L, FareEngine.calculatePaise(1000, 1500, 5000, 1000));
        eq(4150L, FareEngine.calculatePaise(4000, 1500, 3000, 100));
        boolean threw = false;
        try { FareEngine.calculatePaise(4000, 1500, 3000, -1); }
        catch (IllegalArgumentException e) { threw = true; }
        if (!threw) throw new AssertionError("Negative distance must fail");
        double coimbatoreApprox = GeoMath.distanceMeters(11.0168, 76.9558, 11.0268, 76.9558);
        if (Math.abs(coimbatoreApprox - 1112) > 10) throw new AssertionError("Haversine sanity: " + coimbatoreApprox);
        if (GeoMath.distanceMeters(0, 0, 0, 0) != 0) throw new AssertionError("Zero distance");
        System.out.println("PASS: fare arithmetic, minimum fare, validation, GPS distance (7 assertions)");
    }
}
