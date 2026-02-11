package cmr.spatial.geometry;

import cmr.spatial.internal.arc.Arc;
import cmr.spatial.internal.arc.ArcLineSegmentIntersections;
import cmr.spatial.shape.Mbr;
import cmr.spatial.shape.Point;
import cmr.spatial.internal.segment.LineSegment;
import cmr.spatial.math.MathUtils;
import cmr.spatial.internal.ring.CartesianRing;
import cmr.spatial.internal.ring.GeodeticRing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides high-level ring intersection APIs that work with both GeodeticRing and CartesianRing.
 * 
 * This class implements the core ring intersection algorithms from ring_relations.clj:
 * - Ring-to-ring intersections and covers
 * - Ring-to-bounding-rectangle intersections and covers
 * - Type-safe dispatch for geodetic vs cartesian coordinate systems
 * 
 * Key algorithms:
 * - intersectsRing: O(n²) segment intersection check with point containment fallback
 * - coversRing: All points covered + no segment intersections
 * - intersectsBr: MBR pre-check + point containment + segment-side intersections
 * - coversBr: MBR covers + corner points inside + no improper intersections
 * 
 * Implementation based on cmr.spatial.ring-relations Clojure namespace.
 */
public class RingIntersections {
    
    /**
     * Tolerance for approximate equality in MBR covers calculations.
     */
    private static final double COVERS_TOLERANCE = MathUtils.COVERS_TOLERANCE;
    
    // ==================== Ring-to-Ring Intersections ====================
    
    /**
     * Tests if two rings intersect each other.
     * 
     * Two rings intersect if:
     * 1. Any of their segments intersect (checks all pairs - O(n²)), OR
     * 2. Ring2's first point is inside ring1, OR
     * 3. Ring1's first point is inside ring2
     * 
     * Type dispatch handles both GeodeticRing (using Arc segments) and CartesianRing
     * (using LineSegment segments).
     * 
     * Algorithm from ring_relations.clj:intersects-ring?
     * 
     * @param ring1 First ring (GeodeticRing or CartesianRing)
     * @param ring2 Second ring (GeodeticRing or CartesianRing)
     * @return true if the rings intersect
     * @throws IllegalArgumentException if rings are not GeodeticRing or CartesianRing
     */
    public static boolean intersectsRing(Object ring1, Object ring2) {
        Object[] segments1 = getSegments(ring1);
        Object[] segments2 = getSegments(ring2);
        Point[] points1 = getRingPoints(ring1);
        Point[] points2 = getRingPoints(ring2);
        
        // Do any segments intersect? O(n²) check
        for (Object seg1 : segments1) {
            for (Object seg2 : segments2) {
                if (ArcLineSegmentIntersections.intersects(seg1, seg2)) {
                    return true;
                }
            }
        }
        
        // Is ring2 inside ring1? Only need to check first point
        if (points2.length > 0 && ringCoversPoint(ring1, points2[0])) {
            return true;
        }
        
        // Is ring1 inside ring2? Only need to check first point
        if (points1.length > 0 && ringCoversPoint(ring2, points1[0])) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Tests if ring1 completely covers ring2.
     * 
     * A ring covers another ring if:
     * 1. All points of ring2 are inside ring1 (uses covers-point? for each), AND
     * 2. None of ring2's segments intersect ring1's segments
     * 
     * Algorithm from ring_relations.clj:covers-ring?
     * 
     * @param ring1 Outer ring (GeodeticRing or CartesianRing)
     * @param ring2 Inner ring to test (GeodeticRing or CartesianRing)
     * @return true if ring1 completely covers ring2
     * @throws IllegalArgumentException if rings are not GeodeticRing or CartesianRing
     */
    public static boolean coversRing(Object ring1, Object ring2) {
        Point[] points2 = getRingPoints(ring2);
        Object[] segments1 = getSegments(ring1);
        Object[] segments2 = getSegments(ring2);
        
        // All points of ring2 must be inside ring1
        for (Point p : points2) {
            if (!ringCoversPoint(ring1, p)) {
                return false;
            }
        }
        
        // No segments of ring2 should intersect segments of ring1
        for (Object seg2 : segments2) {
            for (Object seg1 : segments1) {
                if (ArcLineSegmentIntersections.intersects(seg1, seg2)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    // ==================== Ring-to-Point Intersections ====================
    
    /**
     * Tests if a ring covers (contains) a point.
     * 
     * @param ring The ring (GeodeticRing or CartesianRing)
     * @param point The point to test
     * @return true if the ring covers the point
     * @throws IllegalArgumentException if ring type is not supported
     */
    public static boolean coversPoint(Object ring, Point point) {
        return ringCoversPoint(ring, point);
    }
    
    // ==================== Ring-to-LineString Intersections ====================
    
    /**
     * Tests if a ring intersects a line string.
     * A ring intersects a line string if:
     * - Any line string point is covered by the ring, OR
     * - Any line string segment intersects any ring segment
     * 
     * @param ring The ring (GeodeticRing or CartesianRing)
     * @param lineString The line string
     * @return true if the ring intersects the line string
     */
    public static boolean intersectsLineString(Object ring, cmr.spatial.shape.LineString lineString) {
        // Get line string points and segments
        List<cmr.spatial.shape.Point> linePoints = ordinatesToPoints(lineString.getOrdinates());
        
        // Check if any line points are covered by the ring
        for (cmr.spatial.shape.Point point : linePoints) {
            if (coversPoint(ring, point)) {
                return true;
            }
        }
        
        // Get segments from both shapes
        Object[] ringSegments = getSegments(ring);
        Object[] lineSegments = getLineStringSegments(lineString, linePoints);
        
        // Check if any line segment intersects any ring segment
        for (Object lineSeg : lineSegments) {
            for (Object ringSeg : ringSegments) {
                if (cmr.spatial.internal.arc.ArcLineSegmentIntersections.intersects(lineSeg, ringSeg)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Tests if a ring covers a line string.
     * A ring covers a line string if:
     * - All line string points are covered by the ring, AND
     * - None of the line string segments intersect the ring segments
     *   (no crossings - the line must stay entirely inside the ring)
     * 
     * @param ring The ring (GeodeticRing or CartesianRing)
     * @param lineString The line string
     * @return true if the ring covers the line string
     */
    public static boolean coversLineString(Object ring, cmr.spatial.shape.LineString lineString) {
        // Get line string points and segments
        List<cmr.spatial.shape.Point> linePoints = ordinatesToPoints(lineString.getOrdinates());
        
        // All line points must be covered by the ring
        for (cmr.spatial.shape.Point point : linePoints) {
            if (!coversPoint(ring, point)) {
                return false;
            }
        }
        
        // Get segments from both shapes
        Object[] ringSegments = getSegments(ring);
        Object[] lineSegments = getLineStringSegments(lineString, linePoints);
        
        // No line segment should intersect any ring segment
        // (If they intersect, the line crosses the ring boundary)
        for (Object lineSeg : lineSegments) {
            for (Object ringSeg : ringSegments) {
                if (cmr.spatial.internal.arc.ArcLineSegmentIntersections.intersects(lineSeg, ringSeg)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    // ==================== Ring-to-BR Intersections ====================
    
    /**
     * Tests if a ring intersects a bounding rectangle.
     * 
     * Algorithm from ring_relations.clj:intersects-br?
     * 
     * Pre-check: Ring's MBR must intersect BR (early exit if not)
     * 
     * Special case: If BR is a single point, delegate to coversPoint
     * 
     * General case - ring intersects BR if any of:
     * 1. BR covers any points of the ring (geodetic vs cartesian check)
     * 2. Ring contains any corner point of the BR (check just one corner)
     * 3. Any ring segment intersects any BR side (optimized loop)
     * 
     * @param ring The ring (GeodeticRing or CartesianRing)
     * @param br The bounding rectangle
     * @return true if the ring intersects the BR
     * @throws IllegalArgumentException if ring is not GeodeticRing or CartesianRing
     */
    public static boolean intersectsBr(Object ring, Mbr br) {
        String coordSys = getCoordinateSystem(ring);
        Mbr ringMbr = getRingMbr(ring);
        
        // Pre-check: do MBRs intersect?
        if (!MbrIntersections.mbrsIntersect(coordSys, ringMbr, br)) {
            return false;
        }
        
        // Special case: single-point BR
        if (isSinglePoint(br)) {
            Point point = new Point(br.getWest(), br.getNorth());
            return ringCoversPoint(ring, point);
        }
        
        Point[] ringPoints = getRingPoints(ring);
        boolean isGeodetic = "geodetic".equals(coordSys);
        
        // Does BR cover any ring points?
        for (Point p : ringPoints) {
            if (isGeodetic) {
                if (geodeticCoversPoint(br, p)) {
                    return true;
                }
            } else {
                if (cartesianCoversPoint(br, p)) {
                    return true;
                }
            }
        }
        
        // Does ring contain any BR corner point? (just check one)
        Point brCorner = new Point(br.getWest(), br.getNorth());
        if (ringCoversPoint(ring, brCorner)) {
            return true;
        }
        
        // Do any ring segments intersect BR sides?
        Object[] segments = getSegments(ring);
        return linesIntersectsBrSides(segments, br);
    }
    
    /**
     * Tests if a ring completely covers a bounding rectangle.
     * 
     * Algorithm from ring_relations.clj:covers-br?
     * 
     * A ring covers a BR if:
     * 1. The ring's MBR covers the BR
     * 2. All corner points of the BR are inside the ring
     * 3. The ring segments do not intersect the BR except at corner/ring points
     * 
     * @param ring The ring (GeodeticRing or CartesianRing)
     * @param br The bounding rectangle
     * @return true if the ring completely covers the BR
     * @throws IllegalArgumentException if ring is not GeodeticRing or CartesianRing
     */
    public static boolean coversBr(Object ring, Mbr br) {
        String coordSys = getCoordinateSystem(ring);
        Mbr ringMbr = getRingMbr(ring);
        
        // Ring's MBR must cover the BR
        if (!coversMbr(coordSys, ringMbr, br)) {
            return false;
        }
        
        // All corner points must be inside the ring
        List<Point> cornerPoints = getCornerPoints(br);
        for (Point corner : cornerPoints) {
            if (!ringCoversPoint(ring, corner)) {
                return false;
            }
        }
        
        // Ring segments should not intersect BR except at acceptable points
        Set<Point> acceptablePoints = new HashSet<>();
        for (Point p : getRingPoints(ring)) {
            acceptablePoints.add(p);
        }
        for (Point p : cornerPoints) {
            acceptablePoints.add(p);
        }
        
        List<Point> intersections = brIntersections(ring, br);
        
        // Either no intersections, or all intersections are acceptable points
        if (intersections.isEmpty()) {
            return true;
        }
        
        for (Point intersection : intersections) {
            if (!containsApproximatePoint(acceptablePoints, intersection)) {
                return false;
            }
        }
        
        return true;
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Returns intersection points where ring segments intersect BR sides.
     * 
     * Algorithm from ring_relations.clj:br-intersections
     * 
     * Special case: If BR is a single point, check if point lies on any ring segment
     * General case: Find all intersections between ring segments and BR line segments
     * 
     * @param ring The ring (GeodeticRing or CartesianRing)
     * @param br The bounding rectangle
     * @return List of intersection points
     */
    public static List<Point> brIntersections(Object ring, Mbr br) {
        List<Point> result = new ArrayList<>();
        
        String coordSys = getCoordinateSystem(ring);
        Mbr ringMbr = getRingMbr(ring);
        
        // Pre-check: do MBRs intersect?
        if (!MbrIntersections.mbrsIntersect(coordSys, ringMbr, br)) {
            return result;
        }
        
        // Special case: single-point BR
        if (isSinglePoint(br)) {
            Point point = new Point(br.getWest(), br.getNorth());
            Object[] segments = getSegments(ring);
            
            for (Object seg : segments) {
                if (intersectsPoint(seg, point)) {
                    result.add(point);
                    return result;
                }
            }
            return result;
        }
        
        // General case: find all segment-side intersections
        Object[] ringSegments = getSegments(ring);
        List<LineSegment> brSides = LineSegment.mbrToLineSegments(br);
        
        for (Object ringSeg : ringSegments) {
            for (LineSegment brSide : brSides) {
                List<Point> segmentIntersections = 
                    ArcLineSegmentIntersections.intersections(ringSeg, brSide);
                result.addAll(segmentIntersections);
            }
        }
        
        return result;
    }
    
    /**
     * Optimized check if any ring segment intersects any BR side.
     * 
     * Algorithm from ring_relations.clj:lines-intersects-br-sides?
     * 
     * Avoids full cartesian product iteration by checking each segment against
     * all sides with early exit. BR can have up to 6 sides if it crosses the
     * antimeridian.
     * 
     * @param segments Array of ring segments (Arc[] or LineSegment[])
     * @param br The bounding rectangle
     * @return true if any segment intersects any BR side
     */
    public static boolean linesIntersectsBrSides(Object[] segments, Mbr br) {
        List<LineSegment> sides = LineSegment.mbrToLineSegments(br);
        
        // Optimized: check each ring segment against all sides with early exit
        for (Object segment : segments) {
            for (LineSegment side : sides) {
                if (ArcLineSegmentIntersections.intersects(segment, side)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    // ==================== Type Dispatch Helpers ====================
    
    /**
     * Gets the segments (arcs or line segments) from a ring.
     * 
     * @param ring GeodeticRing or CartesianRing (already converted from Clojure by caller)
     * @return Arc[] for geodetic rings, LineSegment[] for cartesian rings (Java objects)
     * @throws IllegalArgumentException if ring type is not supported
     */
    private static Object[] getSegments(Object ring) {
        if (ring instanceof GeodeticRing) {
            return ((GeodeticRing) ring).getArcs();
        } else if (ring instanceof CartesianRing) {
            return ((CartesianRing) ring).getLineSegments();
        } else {
            throw new IllegalArgumentException(
                "Ring must be GeodeticRing or CartesianRing (Clojure rings should be converted by caller), got: " + 
                ring.getClass().getName());
        }
    }
    
    /**
     * Gets the coordinate system of a ring.
     * 
     * @param ring GeodeticRing or CartesianRing
     * @return "geodetic" or "cartesian"
     * @throws IllegalArgumentException if ring type is not supported
     */
    private static String getCoordinateSystem(Object ring) {
        if (ring instanceof GeodeticRing) {
            return "geodetic";
        } else if (ring instanceof CartesianRing) {
            return "cartesian";
        } else {
            throw new IllegalArgumentException(
                "Ring must be GeodeticRing or CartesianRing (Clojure rings should be converted by caller), got: " + 
                ring.getClass().getName());
        }
    }
    
    /**
     * Gets the points array from a ring.
     * 
     * @param ring GeodeticRing or CartesianRing
     * @return Array of points defining the ring
     * @throws IllegalArgumentException if ring type is not supported
     */
    private static Point[] getRingPoints(Object ring) {
        if (ring instanceof GeodeticRing) {
            return ((GeodeticRing) ring).getPoints();
        } else if (ring instanceof CartesianRing) {
            return ((CartesianRing) ring).getPoints();
        } else {
            throw new IllegalArgumentException(
                "Ring must be GeodeticRing or CartesianRing (Clojure rings should be converted by caller), got: " + 
                ring.getClass().getName());
        }
    }
    
    /**
     * Gets the MBR from a ring.
     * 
     * @param ring GeodeticRing or CartesianRing
     * @return The ring's minimum bounding rectangle
     * @throws IllegalArgumentException if ring type is not supported
     */
    private static Mbr getRingMbr(Object ring) {
        if (ring instanceof GeodeticRing) {
            return ((GeodeticRing) ring).getMbr();
        } else if (ring instanceof CartesianRing) {
            return ((CartesianRing) ring).getMbr();
        } else {
            throw new IllegalArgumentException(
                "Ring must be GeodeticRing or CartesianRing (Clojure rings should be converted by caller), got: " + 
                ring.getClass().getName());
        }
    }
    
    /**
     * Tests if a ring covers a point using the appropriate algorithm.
     * 
     * @param ring GeodeticRing or CartesianRing
     * @param point The point to test
     * @return true if the ring covers the point
     * @throws IllegalArgumentException if ring type is not supported
     */
    private static boolean ringCoversPoint(Object ring, Point point) {
        if (ring instanceof GeodeticRing) {
            return ((GeodeticRing) ring).coversPoint(point);
        } else if (ring instanceof CartesianRing) {
            return ((CartesianRing) ring).coversPoint(point);
        } else {
            throw new IllegalArgumentException(
                "Ring must be GeodeticRing or CartesianRing (Clojure rings should be converted by caller), got: " + 
                ring.getClass().getName());
        }
    }
    
    // ==================== MBR Utility Methods ====================
    
    /**
     * Tests if an MBR is a single point.
     * 
     * @param mbr The bounding rectangle
     * @return true if west==east and north==south
     */
    private static boolean isSinglePoint(Mbr mbr) {
        return mbr.getWest() == mbr.getEast() && 
               mbr.getNorth() == mbr.getSouth();
    }
    
    /**
     * Returns the corner points of an MBR.
     * Order: upper-left, upper-right, lower-right, lower-left
     * 
     * @param br The bounding rectangle
     * @return List of 4 corner points
     */
    private static List<Point> getCornerPoints(Mbr br) {
        List<Point> corners = new ArrayList<>();
        double west = br.getWest();
        double east = br.getEast();
        double north = br.getNorth();
        double south = br.getSouth();
        
        corners.add(new Point(west, north));   // Upper left
        corners.add(new Point(east, north));   // Upper right
        corners.add(new Point(east, south));   // Lower right
        corners.add(new Point(west, south));   // Lower left
        
        return corners;
    }
    
    /**
     * Tests if MBR1 covers MBR2 using the specified coordinate system.
     * 
     * @param coordSys "geodetic" or "cartesian"
     * @param mbr1 First MBR (should cover mbr2)
     * @param mbr2 Second MBR (should be covered)
     * @return true if mbr1 covers mbr2
     */
    private static boolean coversMbr(String coordSys, Mbr mbr1, Mbr mbr2) {
        List<Point> corners = getCornerPoints(mbr2);
        
        if ("geodetic".equals(coordSys)) {
            for (Point corner : corners) {
                if (!geodeticCoversPoint(mbr1, corner)) {
                    return false;
                }
            }
        } else {
            for (Point corner : corners) {
                if (!cartesianCoversPoint(mbr1, corner)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Tests if an MBR covers a point using geodetic coordinate system.
     * Handles poles and antimeridian crossing.
     * 
     * @param mbr The bounding rectangle
     * @param p The point
     * @return true if the MBR covers the point
     */
    private static boolean geodeticCoversPoint(Mbr mbr, Point p) {
        double tolerance = COVERS_TOLERANCE;
        double lat = p.getLat();
        double lon = p.getLon();
        
        // Handle poles
        if (MathUtils.doubleApprox(lat, 90.0, tolerance)) {
            return coversLat(mbr, 90.0, tolerance);
        }
        if (MathUtils.doubleApprox(lat, -90.0, tolerance)) {
            return coversLat(mbr, -90.0, tolerance);
        }
        
        return coversLat(mbr, lat, tolerance) && 
               coversLonGeodetic(mbr, lon, tolerance);
    }
    
    /**
     * Tests if an MBR covers a point using cartesian coordinate system.
     * 
     * @param mbr The bounding rectangle
     * @param p The point
     * @return true if the MBR covers the point
     */
    private static boolean cartesianCoversPoint(Mbr mbr, Point p) {
        double tolerance = COVERS_TOLERANCE;
        double lat = p.getLat();
        double lon = p.getLon();
        
        return coversLat(mbr, lat, tolerance) && 
               coversLonCartesian(mbr, lon, tolerance);
    }
    
    /**
     * Tests if an MBR covers a latitude value.
     * 
     * @param mbr The bounding rectangle
     * @param lat The latitude
     * @param tolerance Tolerance for approximate equality
     * @return true if south <= lat <= north (within tolerance)
     */
    private static boolean coversLat(Mbr mbr, double lat, double tolerance) {
        double north = mbr.getNorth() + tolerance;
        double south = mbr.getSouth() - tolerance;
        return lat >= south && lat <= north;
    }
    
    /**
     * Tests if an MBR covers a longitude value using geodetic rules.
     * Handles antimeridian crossing and ±180° edge cases.
     * 
     * @param mbr The bounding rectangle
     * @param lon The longitude
     * @param tolerance Tolerance for approximate equality
     * @return true if the MBR covers the longitude
     */
    private static boolean coversLonGeodetic(Mbr mbr, double lon, double tolerance) {
        double west = mbr.getWest();
        double east = mbr.getEast();
        
        // Check for antimeridian crossing
        if (west > east) {
            // MBR crosses antimeridian
            return lon >= (west - tolerance) || lon <= (east + tolerance);
        } else if (Math.abs(lon) == 180.0) {
            double within180 = 180.0 - tolerance;
            return Math.abs(west) >= within180 || Math.abs(east) >= within180;
        } else {
            return lon >= (west - tolerance) && lon <= (east + tolerance);
        }
    }
    
    /**
     * Tests if an MBR covers a longitude value using cartesian rules.
     * Simple range check without antimeridian handling.
     * 
     * @param mbr The bounding rectangle
     * @param lon The longitude
     * @param tolerance Tolerance for approximate equality
     * @return true if west <= lon <= east (within tolerance)
     */
    private static boolean coversLonCartesian(Mbr mbr, double lon, double tolerance) {
        double west = mbr.getWest() - tolerance;
        double east = mbr.getEast() + tolerance;
        return lon >= west && lon <= east;
    }
    
    // ==================== Point/Segment Utilities ====================
    
    /**
     * Tests if a segment (Arc or LineSegment) intersects a point.
     * 
     * @param segment Arc or LineSegment
     * @param point The point to test
     * @return true if the segment contains the point
     */
    private static boolean intersectsPoint(Object segment, Point point) {
        if (segment instanceof Arc) {
            Arc arc = (Arc) segment;
            // Check if point lies on arc endpoints first
            Point west = arc.getWestPoint();
            Point east = arc.getEastPoint();
            
            if (pointsApproxEqual(point, west) || pointsApproxEqual(point, east)) {
                return true;
            }
            
            // Use precise point-on-arc test instead of MBR approximation
            return arc.pointOnArc(point);
            
        } else if (segment instanceof LineSegment) {
            LineSegment ls = (LineSegment) segment;
            return ls.pointOnSegment(point);
        } else {
            throw new IllegalArgumentException(
                "Segment must be Arc or LineSegment, got: " + 
                segment.getClass().getName());
        }
    }
    
    /**
     * Checks if two points are approximately equal.
     * 
     * @param p1 First point
     * @param p2 Second point
     * @return true if points are within tolerance
     */
    private static boolean pointsApproxEqual(Point p1, Point p2) {
        return MathUtils.doubleApprox(p1.getLon(), p2.getLon(), COVERS_TOLERANCE) &&
               MathUtils.doubleApprox(p1.getLat(), p2.getLat(), COVERS_TOLERANCE);
    }
    
    /**
     * Checks if a set contains a point within approximate tolerance.
     * 
     * @param points Set of points
     * @param target Target point to find
     * @return true if set contains an approximately equal point
     */
    private static boolean containsApproximatePoint(Set<Point> points, Point target) {
        for (Point p : points) {
            if (pointsApproxEqual(p, target)) {
                return true;
            }
        }
        return false;
    }
    
    // ==================== LineString Helper Methods ====================
    
    /**
     * Converts ordinates to a list of points.
     * 
     * @param ordinates List of coordinates [lon1, lat1, lon2, lat2, ...]
     * @return List of Point objects
     */
    private static List<Point> ordinatesToPoints(List<Double> ordinates) {
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < ordinates.size(); i += 2) {
            points.add(new Point(ordinates.get(i), ordinates.get(i + 1)));
        }
        return points;
    }
    
    /**
     * Gets segments from a LineString.
     * 
     * @param lineString The line string
     * @param points Pre-computed points (to avoid recomputing)
     * @return Array of segments (Arc for geodetic, LineSegment for cartesian)
     */
    private static Object[] getLineStringSegments(cmr.spatial.shape.LineString lineString, List<Point> points) {
        String coordSystem = lineString.getCoordinateSystem();
        
        if ("geodetic".equals(coordSystem)) {
            List<Arc> arcs = new ArrayList<>();
            for (int i = 0; i < points.size() - 1; i++) {
                try {
                    arcs.add(Arc.createArc(points.get(i), points.get(i + 1)));
                } catch (IllegalArgumentException e) {
                    // Skip invalid arcs (duplicate or antipodal points)
                    // This can happen with degenerate linestrings
                }
            }
            return arcs.toArray();
        } else { // cartesian
            return LineSegment.pointsToLineSegments(points).toArray();
        }
    }
    
    // ==================== Ring Factory Methods ====================
    
    /**
     * Converts a Ring data holder to its Java implementation (GeodeticRing or CartesianRing).
     * This is used by PolygonIntersections to work with Ring objects from Polygon shapes.
     * 
     * @param ring The Ring data holder with coordinate system and ordinates
     * @return GeodeticRing or CartesianRing depending on coordinate system
     * @throws IllegalArgumentException if ring is null or coordinate system is invalid
     */
    public static Object createJavaRing(cmr.spatial.shape.Ring ring) {
        if (ring == null) {
            throw new IllegalArgumentException("Ring cannot be null");
        }
        
        List<Point> points = ordinatesToPoints(ring.getOrdinates());
        String coordSystem = ring.getCoordinateSystem();
        
        if ("geodetic".equals(coordSystem)) {
            return GeodeticRing.createRing(points);
        } else if ("cartesian".equals(coordSystem)) {
            return CartesianRing.createRing(points);
        } else {
            throw new IllegalArgumentException(
                "Invalid coordinate system: " + coordSystem + " (expected 'geodetic' or 'cartesian')");
        }
    }
}
