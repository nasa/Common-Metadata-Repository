package cmr.spatial.geometry;

import cmr.spatial.shape.Point;
import cmr.spatial.math.MathUtils;

/**
 * Implements Point-to-Point intersection testing.
 * Translates the covers-point? protocol method for Point from Clojure.
 */
public class PointIntersections {

    /**
     * Tests if two points are equal (intersect).
     * Points are considered equal if they are approximately equal within DELTA tolerance.
     *
     * @param p1 First point
     * @param p2 Second point
     * @return true if points are approximately equal
     */
    public static boolean pointsIntersect(Point p1, Point p2) {
        return pointsApproxEqual(p1, p2, MathUtils.DELTA);
    }

    /**
     * Tests if two points are approximately equal within the given delta.
     * Handles special cases for geodetic (spherical) equality:
     * - Latitudes must match within tolerance
     * - Longitudes can match directly, or both be on antimeridian, or both at poles
     * - All longitudes are equivalent at either pole (±90°)
     * - -180 and 180 are equivalent longitudes
     *
     * @param p1 First point
     * @param p2 Second point
     * @param delta Tolerance for approximate equality
     * @return true if points are approximately equal within tolerance
     */
    public static boolean pointsApproxEqual(Point p1, Point p2, double delta) {
        double lat1 = p1.getLat();
        double lat2 = p2.getLat();
        double lon1 = p1.getLon();
        double lon2 = p2.getLon();

        // Latitudes must match
        if (!MathUtils.doubleApprox(lat1, lat2, delta)) {
            return false;
        }

        // Check longitude equivalence
        if (MathUtils.doubleApprox(lon1, lon2, delta)) {
            return true;
        }

        // Check if both on antimeridian (-180 or 180)
        if (isOnAntimeridian(lon1) && isOnAntimeridian(lon2)) {
            return true;
        }

        // Check if both at north pole
        if (isNorthPole(lat1, delta) && isNorthPole(lat2, delta)) {
            return true;
        }

        // Check if both at south pole
        if (isSouthPole(lat1, delta) && isSouthPole(lat2, delta)) {
            return true;
        }

        return false;
    }

    /**
     * Returns true if latitude is approximately the north pole (90.0).
     */
    public static boolean isNorthPole(double lat, double delta) {
        return MathUtils.doubleApprox(lat, 90.0, delta);
    }

    /**
     * Returns true if latitude is approximately the south pole (-90.0).
     */
    public static boolean isSouthPole(double lat, double delta) {
        return MathUtils.doubleApprox(lat, -90.0, delta);
    }

    /**
     * Returns true if longitude is on the antimeridian (-180 or 180).
     */
    private static boolean isOnAntimeridian(double lon) {
        return MathUtils.abs(lon) == 180.0;
    }
}
