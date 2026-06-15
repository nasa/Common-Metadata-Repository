package cmr.spatial.geometry;

import cmr.spatial.shape.Point;
import cmr.spatial.shape.Mbr;
import cmr.spatial.math.MathUtils;

/**
 * Implements Point-to-Mbr intersection testing.
 * Translates the covers-point? and intersects-br? protocol methods from Clojure.
 */
public class PointMbrIntersections {

    /**
     * Tests if a point intersects with an MBR.
     * A point intersects an MBR if the MBR covers the point.
     *
     * @param point The point to test
     * @param mbr The minimum bounding rectangle
     * @return true if the MBR covers the point
     */
    public static boolean pointIntersectsMbr(Point point, Mbr mbr) {
        return geodetic_covers_point(mbr, point);
    }

    /**
     * Tests if an MBR covers a point using geodetic (spherical) coordinate system.
     * Handles special cases for poles and normal latitude/longitude coverage.
     *
     * @param mbr The minimum bounding rectangle
     * @param point The point to test
     * @return true if the MBR covers the point
     */
    public static boolean geodetic_covers_point(Mbr mbr, Point point) {
        return geodetic_covers_point(mbr, point, MathUtils.COVERS_TOLERANCE);
    }

    /**
     * Tests if an MBR covers a point using geodetic coordinate system with custom delta.
     *
     * @param mbr The minimum bounding rectangle
     * @param point The point to test
     * @param delta Tolerance for coverage checks
     * @return true if the MBR covers the point
     */
    public static boolean geodetic_covers_point(Mbr mbr, Point point, double delta) {
        double lat = point.getLat();
        double lon = point.getLon();

        // Special case: north pole
        if (PointIntersections.isNorthPole(lat, delta)) {
            return covers_lat(mbr, 90.0, delta);
        }

        // Special case: south pole
        if (PointIntersections.isSouthPole(lat, delta)) {
            return covers_lat(mbr, -90.0, delta);
        }

        // Normal case: check latitude and longitude coverage
        return covers_lat(mbr, lat, delta)
            && geodetic_lon_range_covers_lon(mbr.getWest(), mbr.getEast(), lon, delta);
    }

    /**
     * Returns true if the MBR covers the given latitude.
     * A latitude is covered if it falls within the MBR's north and south bounds.
     *
     * @param mbr The minimum bounding rectangle
     * @param lat The latitude to check
     * @param delta Tolerance
     * @return true if the MBR covers the latitude
     */
    public static boolean covers_lat(Mbr mbr, double lat, double delta) {
        double north = mbr.getNorth() + delta;
        double south = mbr.getSouth() - delta;
        return lat >= south && lat <= north;
    }

    /**
     * Returns true if a longitude is within the geodetic range.
     * Handles antimeridian crossing: if the range crosses ±180°, it wraps around.
     * For a normal range (west < east), the longitude must be between them.
     * For a crossing range (west > east), the longitude must be either >= west or <= east.
     *
     * @param west Western bound of the longitude range
     * @param east Eastern bound of the longitude range
     * @param lon The longitude to check
     * @param delta Tolerance
     * @return true if longitude is in range
     */
    public static boolean geodetic_lon_range_covers_lon(double west, double east,
                                                        double lon, double delta) {
        double west_adjusted = west - delta;
        double east_adjusted = east + delta;
        boolean crosses_antimeridian = west_adjusted > east_adjusted;

        if (crosses_antimeridian) {
            // Range wraps around antimeridian
            return lon >= west_adjusted || lon <= east_adjusted;
        } else {
            // Normal range
            return lon >= west_adjusted && lon <= east_adjusted;
        }
    }

    /**
     * Helper method: returns true if latitude is north pole.
     */
    private static boolean isNorthPole(double lat, double delta) {
        return PointIntersections.isNorthPole(lat, delta);
    }

    /**
     * Helper method: returns true if latitude is south pole.
     */
    private static boolean isSouthPole(double lat, double delta) {
        return PointIntersections.isSouthPole(lat, delta);
    }
}
