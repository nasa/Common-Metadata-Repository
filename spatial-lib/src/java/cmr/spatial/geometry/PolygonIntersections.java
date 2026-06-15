package cmr.spatial.geometry;

import cmr.spatial.shape.LineString;
import cmr.spatial.shape.Mbr;
import cmr.spatial.shape.Point;
import cmr.spatial.shape.Polygon;
import cmr.spatial.shape.Ring;
import cmr.spatial.math.MathUtils;
import java.util.List;

/**
 * Implements Polygon intersection testing.
 * Translates covers-point?, covers-br?, intersects-br?, intersects-ring?, intersects-polygon?,
 * and intersects-line-string? from polygon.clj.
 * 
 * A polygon consists of an outer boundary ring and optional inner rings (holes).
 * Intersection and coverage tests delegate to the ring operations.
 */
public class PolygonIntersections {

    /**
     * Tests if a polygon covers a point.
     * A polygon covers a point if:
     * - The outer boundary ring covers the point, AND
     * - NONE of the holes cover the point
     *
     * @param polygon The polygon to test
     * @param point The point to check
     * @return true if the polygon covers the point
     */
    public static boolean coversPoint(Polygon polygon, Point point) {
        Ring boundary = polygon.getBoundary();
        if (boundary == null) {
            return false;
        }

        // Convert Ring data holder to Java ring implementation
        Object javaRing = RingIntersections.createJavaRing(boundary);
        
        // Outer ring must cover the point
        if (!RingIntersections.coversPoint(javaRing, point)) {
            return false;
        }

        // None of the holes should cover the point
        for (Ring hole : polygon.getHoles()) {
            Object javaHole = RingIntersections.createJavaRing(hole);
            if (RingIntersections.coversPoint(javaHole, point)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Tests if a polygon covers a minimal bounding rectangle (MBR).
     * A polygon covers an MBR if:
     * - The outer boundary ring covers the MBR, AND
     * - NONE of the holes intersect the MBR
     *
     * @param polygon The polygon to test
     * @param mbr The bounding rectangle
     * @return true if the polygon covers the MBR
     */
    public static boolean coversBr(Polygon polygon, Mbr mbr) {
        Ring boundary = polygon.getBoundary();
        if (boundary == null) {
            return false;
        }

        // Convert Ring data holder to Java ring implementation
        Object javaRing = RingIntersections.createJavaRing(boundary);
        
        // Outer ring must cover the MBR
        if (!RingIntersections.coversBr(javaRing, mbr)) {
            return false;
        }

        // None of the holes should intersect the MBR
        for (Ring hole : polygon.getHoles()) {
            Object javaHole = RingIntersections.createJavaRing(hole);
            if (RingIntersections.intersectsBr(javaHole, mbr)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Tests if a polygon intersects a minimal bounding rectangle (MBR).
     * A polygon intersects an MBR if:
     * - The outer boundary ring intersects the MBR, AND
     * - NONE of the holes cover the MBR
     *
     * @param polygon The polygon to test
     * @param mbr The bounding rectangle
     * @return true if the polygon intersects the MBR
     */
    public static boolean intersectsBr(Polygon polygon, Mbr mbr) {
        Ring boundary = polygon.getBoundary();
        if (boundary == null) {
            return false;
        }

        // Convert Ring data holder to Java ring implementation
        Object javaRing = RingIntersections.createJavaRing(boundary);
        
        // Outer ring must intersect the MBR
        if (!RingIntersections.intersectsBr(javaRing, mbr)) {
            return false;
        }

        // None of the holes should cover the MBR
        for (Ring hole : polygon.getHoles()) {
            Object javaHole = RingIntersections.createJavaRing(hole);
            if (RingIntersections.coversBr(javaHole, mbr)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Tests if a polygon intersects a ring.
     * A polygon intersects a ring if:
     * - The outer boundary ring intersects the ring, AND
     * - NONE of the holes cover the ring
     *
     * @param polygon The polygon to test
     * @param ring The ring to check
     * @return true if the polygon intersects the ring
     */
    public static boolean intersectsRing(Polygon polygon, Ring ring) {
        Ring boundary = polygon.getBoundary();
        if (boundary == null) {
            return false;
        }

        // Convert Ring data holders to Java ring implementations
        Object javaBoundary = RingIntersections.createJavaRing(boundary);
        Object javaRing = RingIntersections.createJavaRing(ring);
        
        // Outer ring must intersect the other ring
        if (!RingIntersections.intersectsRing(javaBoundary, javaRing)) {
            return false;
        }

        // None of the holes should cover the ring
        for (Ring hole : polygon.getHoles()) {
            Object javaHole = RingIntersections.createJavaRing(hole);
            if (RingIntersections.coversRing(javaHole, javaRing)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Tests if a polygon intersects a line string.
     * A polygon intersects a line string if:
     * - The outer boundary ring intersects the line string, AND
     * - The line string is not completely covered by any of the holes
     *
     * @param polygon The polygon to test
     * @param lineString The line string to check
     * @return true if the polygon intersects the line string
     */
    public static boolean intersectsLineString(Polygon polygon, LineString lineString) {
        Ring boundary = polygon.getBoundary();
        if (boundary == null) {
            return false;
        }

        // Convert Ring data holder to Java ring implementation
        Object javaRing = RingIntersections.createJavaRing(boundary);
        
        // Outer ring must intersect the line string
        if (!RingIntersections.intersectsLineString(javaRing, lineString)) {
            return false;
        }

        // None of the holes should cover the line string
        for (Ring hole : polygon.getHoles()) {
            Object javaHole = RingIntersections.createJavaRing(hole);
            if (RingIntersections.coversLineString(javaHole, lineString)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Tests if a polygon intersects another polygon.
     * A polygon intersects another polygon if:
     * - The outer boundary ring of poly1 intersects the outer boundary ring of poly2, AND
     * - NONE of the holes of poly1 cover the outer boundary of poly2, AND
     * - NONE of the holes of poly2 cover the outer boundary of poly1
     *
     * @param polygon1 First polygon
     * @param polygon2 Second polygon
     * @return true if the polygons intersect
     */
    public static boolean intersectsPolygon(Polygon polygon1, Polygon polygon2) {
        Ring boundary1 = polygon1.getBoundary();
        Ring boundary2 = polygon2.getBoundary();

        if (boundary1 == null || boundary2 == null) {
            return false;
        }

        // Convert Ring data holders to Java ring implementations
        Object javaBoundary1 = RingIntersections.createJavaRing(boundary1);
        Object javaBoundary2 = RingIntersections.createJavaRing(boundary2);
        
        // Outer rings must intersect
        if (!RingIntersections.intersectsRing(javaBoundary1, javaBoundary2)) {
            return false;
        }

        // None of the holes of poly1 should cover the boundary of poly2
        for (Ring hole : polygon1.getHoles()) {
            Object javaHole = RingIntersections.createJavaRing(hole);
            if (RingIntersections.coversRing(javaHole, javaBoundary2)) {
                return false;
            }
        }

        // None of the holes of poly2 should cover the boundary of poly1
        for (Ring hole : polygon2.getHoles()) {
            Object javaHole = RingIntersections.createJavaRing(hole);
            if (RingIntersections.coversRing(javaHole, javaBoundary1)) {
                return false;
            }
        }

        return true;
    }
}
