package cmr.spatial.geometry;

import cmr.spatial.shape.Mbr;
import cmr.spatial.math.MathUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements MBR-to-MBR intersection testing.
 * Translates the intersects-br? protocol method from Clojure.
 */
public class MbrIntersections {

    /**
     * Tests if two MBRs intersect using geodetic (spherical) coordinate system.
     * Handles both simple cases (neither crosses antimeridian) and complex cases
     * (one or both cross the antimeridian).
     *
     * @param mbr1 First minimum bounding rectangle
     * @param mbr2 Second minimum bounding rectangle
     * @return true if the MBRs intersect
     */
    public static boolean mbrsIntersect(Mbr mbr1, Mbr mbr2) {
        return mbrsIntersect("geodetic", mbr1, mbr2);
    }

    /**
     * Tests if two MBRs intersect using the specified coordinate system.
     *
     * @param coordSys Coordinate system: "geodetic" or "cartesian"
     * @param mbr1 First minimum bounding rectangle
     * @param mbr2 Second minimum bounding rectangle
     * @return true if the MBRs intersect
     */
    public static boolean mbrsIntersect(String coordSys, Mbr mbr1, Mbr mbr2) {
        // Optimized path: if neither crosses antimeridian, use simpler logic
        if (!crossesAntimeridian(mbr1) && !crossesAntimeridian(mbr2)) {
            return nonCrossingIntersects(coordSys, mbr1, mbr2);
        }

        // Complex path: handle antimeridian crossing
        List<Mbr> mbr1_parts = splitAcrossAntimeridian(mbr1);
        List<Mbr> mbr2_parts = splitAcrossAntimeridian(mbr2);

        // Check all 4 combinations of split parts
        for (Mbr m1 : mbr1_parts) {
            for (Mbr m2 : mbr2_parts) {
                if (nonCrossingIntersects(coordSys, m1, m2)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Tests if two non-antimeridian-crossing MBRs intersect.
     * For two normal (non-wrapping) rectangles, intersection requires both:
     * 1. Longitude ranges to overlap
     * 2. Latitude ranges to overlap
     * Special case: In geodetic coordinates, rectangles touching at poles count as intersecting.
     *
     * @param coordSys Coordinate system
     * @param mbr1 First MBR (must not cross antimeridian)
     * @param mbr2 Second MBR (must not cross antimeridian)
     * @return true if the MBRs intersect
     */
    public static boolean nonCrossingIntersects(String coordSys, Mbr mbr1, Mbr mbr2) {
        double w1 = mbr1.getWest();
        double n1 = mbr1.getNorth();
        double e1 = mbr1.getEast();
        double s1 = mbr1.getSouth();

        double w2 = mbr2.getWest();
        double n2 = mbr2.getNorth();
        double e2 = mbr2.getEast();
        double s2 = mbr2.getSouth();

        // Check if poles are touched
        boolean m1_touches_north = MathUtils.doubleApprox(n1, 90.0, 0.0000001);
        boolean m1_touches_south = MathUtils.doubleApprox(s1, -90.0, 0.0000001);
        boolean m2_touches_north = MathUtils.doubleApprox(n2, 90.0, 0.0000001);
        boolean m2_touches_south = MathUtils.doubleApprox(s2, -90.0, 0.0000001);

        // Main intersection check: both longitude and latitude ranges must intersect
        boolean ranges_intersect = MathUtils.rangeIntersects(w1, e1, w2, e2)
                                && MathUtils.rangeIntersects(s1, n1, s2, n2);

        if (ranges_intersect) {
            return true;
        }

        // Geodetic special case: touching at poles counts as intersection
        if ("geodetic".equals(coordSys)) {
            if ((m1_touches_north && m2_touches_north)
                || (m1_touches_south && m2_touches_south)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true if an MBR crosses the antimeridian (±180° boundary).
     * An MBR crosses if its western bound is greater than its eastern bound.
     *
     * @param mbr The minimum bounding rectangle
     * @return true if the MBR crosses the antimeridian
     */
    public static boolean crossesAntimeridian(Mbr mbr) {
        return mbr.getWest() > mbr.getEast();
    }

    /**
     * Splits an MBR across the antimeridian if it crosses.
     * If the MBR crosses the antimeridian, returns a list of 2 MBRs (east and west portions).
     * If the MBR doesn't cross, returns a list containing just the original MBR.
     * An MBR that crosses is split at ±180° into two non-crossing parts.
     *
     * @param mbr The minimum bounding rectangle
     * @return List of 1 or 2 MBRs
     */
    public static List<Mbr> splitAcrossAntimeridian(Mbr mbr) {
        List<Mbr> result = new ArrayList<>();

        if (crossesAntimeridian(mbr)) {
            // Split into east and west portions
            result.add(new Mbr(mbr.getWest(), mbr.getNorth(), 180.0, mbr.getSouth()));
            result.add(new Mbr(-180.0, mbr.getNorth(), mbr.getEast(), mbr.getSouth()));
        } else {
            result.add(mbr);
        }

        return result;
    }
}
