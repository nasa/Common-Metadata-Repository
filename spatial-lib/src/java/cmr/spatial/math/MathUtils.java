package cmr.spatial.math;

/**
 * Utility functions for spatial math operations.
 * Translates Clojure spatial math functions to Java for use in pure Java intersection testing.
 */
public class MathUtils {

    /**
     * Delta tolerance for floating point approximate equality.
     * Matches CMR spatial-lib DELTA constant.
     */
    public static final double DELTA = 0.000000001;

    /**
     * Tolerance for covers method. Longitudes and latitudes technically outside the
     * bounding rectangle but within this tolerance will be considered covered.
     */
    public static final double COVERS_TOLERANCE = 0.0000000001;

    /**
     * Returns true if v is within min and max (inclusive).
     * Implements the within-range? macro from Clojure.
     */
    public static boolean withinRange(double v, double min, double max) {
        return v >= min && v <= max;
    }

    /**
     * Returns true if two ranges intersect.
     * Implements the range-intersects? macro from Clojure.
     * Returns true if range2 intersects range1.
     *
     * @param r1min Lower bound of range 1
     * @param r1max Upper bound of range 1
     * @param r2min Lower bound of range 2
     * @param r2max Upper bound of range 2
     * @return true if the ranges intersect
     */
    public static boolean rangeIntersects(double r1min, double r1max,
                                          double r2min, double r2max) {
        return withinRange(r2min, r1min, r1max)
            || withinRange(r2max, r1min, r1max)
            || withinRange(r1min, r2min, r2max);
    }

    /**
     * Returns true if a and b are approximately equal within DELTA tolerance.
     * Implements approx= protocol for doubles.
     */
    public static boolean doubleApprox(double a, double b) {
        return doubleApprox(a, b, DELTA);
    }

    /**
     * Returns true if a and b are approximately equal within the given delta tolerance.
     */
    public static boolean doubleApprox(double a, double b, double delta) {
        return Math.abs(a - b) <= delta;
    }

    /**
     * Returns the absolute value of a double.
     */
    public static double abs(double v) {
        return Math.abs(v);
    }

    /**
     * Radius of Earth in meters for distance calculations.
     * Matches EARTH_RADIUS_METERS in math.clj.
     */
    public static final double EARTH_RADIUS_METERS = 6367435.0;

    /**
     * Calculates the angular distance between two points in radians.
     * Uses the Haversine formula.
     * From: http://williams.best.vwh.net/avform.htm#Dist
     * 
     * @param p1 First point
     * @param p2 Second point
     * @return Angular distance in radians
     */
    public static double angularDistance(cmr.spatial.shape.Point p1, cmr.spatial.shape.Point p2) {
        double lon1Rad = Math.toRadians(p1.getLon());
        double lat1Rad = Math.toRadians(p1.getLat());
        double lon2Rad = Math.toRadians(p2.getLon());
        double lat2Rad = Math.toRadians(p2.getLat());
        
        // Haversine formula: a = sin²(Δlat/2) + cos(lat1) * cos(lat2) * sin²(Δlon/2)
        double sinSqLat = sinSquared((lat1Rad - lat2Rad) / 2.0);
        double sinSqLon = sinSquared((lon1Rad - lon2Rad) / 2.0);
        double part1 = sinSqLat;
        double part2 = Math.cos(lat1Rad) * Math.cos(lat2Rad) * sinSqLon;
        
        return 2.0 * Math.asin(Math.sqrt(part1 + part2));
    }

    /**
     * Calculates the distance between two points in meters.
     * 
     * @param p1 First point
     * @param p2 Second point
     * @return Distance in meters
     */
    public static double distance(cmr.spatial.shape.Point p1, cmr.spatial.shape.Point p2) {
        return angularDistance(p1, p2) * EARTH_RADIUS_METERS;
    }

    /**
     * Helper for computing angular distance: sin²(v) = sin(v)²
     */
    private static double sinSquared(double v) {
        double s = Math.sin(v);
        return s * s;
    }
}
