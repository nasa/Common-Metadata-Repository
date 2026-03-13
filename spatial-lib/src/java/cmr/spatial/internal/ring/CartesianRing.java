package cmr.spatial.internal.ring;

import cmr.spatial.shape.Point;
import cmr.spatial.shape.Mbr;
import cmr.spatial.internal.segment.LineSegment;
import cmr.spatial.math.MathUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a cartesian (flat plane) ring - a closed sequence of points
 * connected by straight line segments.
 * 
 * Used for cartesian polygons. Points must be in counter-clockwise order.
 * The last point must match the first point to close the ring.
 * 
 * This class handles:
 * - Ray-casting algorithm for point-in-ring tests
 * - Fixed external point (outside Earth's coordinate bounds)
 * - Simple MBR calculation (no antimeridian crossing)
 * 
 * Based on cmr.spatial.cartesian-ring Clojure namespace.
 */
public class CartesianRing {
    
    private static final double APPROXIMATION_DELTA = LineSegment.COVERS_TOLERANCE;
    private static final double COVERS_TOLERANCE = 0.0000000001;  // Matches cmr.spatial.mbr/COVERS_TOLERANCE
    
    /**
     * Fixed external point that's outside all valid cartesian rings.
     * Uses coordinates outside the Earth's bounds (lon=181, lat=91).
     */
    private static final Point EXTERNAL_POINT = new Point(181.0, 91.0);
    
    private final List<Point> points;
    private final List<LineSegment> lineSegments;
    private final Mbr mbr;
    
    /**
     * Private constructor. Use createRing() factory method instead.
     */
    private CartesianRing(List<Point> points, List<LineSegment> lineSegments, Mbr mbr) {
        this.points = points;
        this.lineSegments = lineSegments;
        this.mbr = mbr;
    }
    
    /**
     * Creates a cartesian ring from a list of points.
     * Calculates derived fields: line segments and MBR.
     * 
     * @param points List of points forming the ring (last must equal first)
     *               Can be either cmr.spatial.shape.Point or cmr.spatial.point.Point (Clojure records)
     * @return A new CartesianRing
     */
    public static CartesianRing createRing(List<?> points) {
        // Convert Clojure Points to Java Points if necessary
        List<Point> javaPoints = convertToJavaPoints(points);
        List<LineSegment> lineSegments = pointsToLineSegments(javaPoints);
        Mbr mbr = calculateMbr(lineSegments);
        
        return new CartesianRing(javaPoints, lineSegments, mbr);
    }
    
    /**
     * Converts a list of objects (potentially Clojure Point records) to Java Points.
     */
    private static List<Point> convertToJavaPoints(List<?> points) {
        List<Point> result = new ArrayList<>();
        for (Object p : points) {
            if (p instanceof Point) {
                result.add((Point) p);
            } else {
                // Assume it's a Clojure Point record with lon/lat fields
                try {
                    java.lang.reflect.Field lonField = p.getClass().getField("lon");
                    java.lang.reflect.Field latField = p.getClass().getField("lat");
                    double lon = ((Number) lonField.get(p)).doubleValue();
                    double lat = ((Number) latField.get(p)).doubleValue();
                    result.add(new Point(lon, lat));
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                        "Could not convert point to Java Point: " + p.getClass().getName(), e);
                }
            }
        }
        return result;
    }
    
    /**
     * Converts a list of points to a list of line segments.
     */
    private static List<LineSegment> pointsToLineSegments(List<Point> points) {
        List<LineSegment> segments = new ArrayList<>();
        for (int i = 0; i < points.size() - 1; i++) {
            Point p1 = points.get(i);
            Point p2 = points.get(i + 1);
            
            // Skip if points are equal
            if (!pointsEqual(p1, p2)) {
                segments.add(LineSegment.createLineSegment(p1, p2));
            }
        }
        return segments;
    }
    
    /**
     * Calculates the minimum bounding rectangle for the ring.
     * For cartesian rings, the MBR does not cross the antimeridian.
     */
    private static Mbr calculateMbr(List<LineSegment> segments) {
        if (segments.isEmpty()) {
            return new Mbr(0.0, 0.0, 0.0, 0.0);
        }
        
        Mbr result = segments.get(0).getMbr();
        
        for (int i = 1; i < segments.size(); i++) {
            Mbr segmentMbr = segments.get(i).getMbr();
            result = unionMbrsCartesian(result, segmentMbr);
        }
        
        return result;
    }
    
    /**
     * Unions two MBRs (cartesian - no antimeridian crossing).
     */
    private static Mbr unionMbrsCartesian(Mbr mbr1, Mbr mbr2) {
        double west = Math.min(mbr1.getWest(), mbr2.getWest());
        double north = Math.max(mbr1.getNorth(), mbr2.getNorth());
        double east = Math.max(mbr1.getEast(), mbr2.getEast());
        double south = Math.min(mbr1.getSouth(), mbr2.getSouth());
        
        return new Mbr(west, north, east, south);
    }
    
    /**
     * Tests if a point is covered by (inside) this ring using ray-casting algorithm.
     * 
     * Algorithm (matching Clojure implementation):
     * 1. First check if MBR covers the point (optimization)
     * 2. Check if point is in the point set
     * 3. Cast a ray from the test point to the external point
     * 4. Count unique intersections (rounded to 5 decimal places)
     * 5. Odd count = inside, even count = outside
     * 
     * @param point The point to test
     * @return true if the ring covers the point
     */
    public boolean coversPoint(Point point) {
        // Only do real intersection if the MBR covers the point (matches Clojure)
        if (!cartesianMbrCoversPoint(mbr, point)) {
            return false;
        }
        
        // Check if point is one of the ring's points (matches Clojure point-set check)
        for (Point p : points) {
            if (pointsEqual(p, point)) {
                return true;
            }
        }
        
        // Create the test ray from point to external point (matches Clojure direction)
        LineSegment crossingLine = LineSegment.createLineSegment(point, EXTERNAL_POINT);
        
        // Find all intersections with ring segments and round them
        Set<Point> intersections = new HashSet<>();
        for (LineSegment segment : lineSegments) {
            Point intersection = crossingLine.intersection(segment);
            if (intersection != null) {
                // Round to 5 decimal places to match Clojure INTERSECTION_POINT_PRECISION
                Point rounded = roundPoint(intersection);
                intersections.add(rounded);
            }
        }
        
        // Check if point itself is one of the intersections (matches Clojure)
        if (intersections.contains(roundPoint(point))) {
            return true;
        }
        
        // Odd number of intersections = inside, even = outside
        return intersections.size() % 2 == 1;
    }
    
    /**
     * Checks if an MBR covers a point in cartesian space.
     * Matches cmr.spatial.mbr/cartesian-covers-point? with default tolerance.
     */
    private static boolean cartesianMbrCoversPoint(Mbr mbr, Point point) {
        double lon = point.getLon();
        double lat = point.getLat();
        double delta = COVERS_TOLERANCE;
        
        // Check latitude with tolerance (covers-lat?)
        double north = mbr.getNorth() + delta;
        double south = mbr.getSouth() - delta;
        if (lat < south || lat > north) {
            return false;
        }
        
        // Check longitude with tolerance (cartesian-lon-range-covers-lon?)
        double west = mbr.getWest() - delta;
        double east = mbr.getEast() + delta;
        boolean crossesAntimeridian = west > east;
        
        if (crossesAntimeridian) {
            return lon >= west || lon <= east;
        } else {
            return lon >= west && lon <= east;
        }
    }
    
    /**
     * Rounds a point to a fixed precision to handle near-duplicates.
     * Uses precision = 5 to match Clojure INTERSECTION_POINT_PRECISION.
     */
    private static Point roundPoint(Point p) {
        double scale = 100000.0;  // 5 decimal places (10^5)
        double lon = Math.round(p.getLon() * scale) / scale;
        double lat = Math.round(p.getLat() * scale) / scale;
        return new Point(lon, lat);
    }
    
    /**
     * Returns true if two points are equal (with tolerance).
     */
    private static boolean pointsEqual(Point p1, Point p2) {
        return MathUtils.doubleApprox(p1.getLon(), p2.getLon(), APPROXIMATION_DELTA) &&
               MathUtils.doubleApprox(p1.getLat(), p2.getLat(), APPROXIMATION_DELTA);
    }
    
    /**
     * Determines the winding order of the ring.
     * Uses the sum-over-area-under-edges algorithm.
     * 
     * @return CLOCKWISE or COUNTER_CLOCKWISE
     */
    public WindingOrder calculateWinding() {
        double sum = 0.0;
        
        for (LineSegment segment : lineSegments) {
            Point p1 = segment.getPoint1();
            Point p2 = segment.getPoint2();
            
            double x1 = p1.getLon();
            double y1 = p1.getLat();
            double x2 = p2.getLon();
            double y2 = p2.getLat();
            
            // Sum (x2 - x1) * (y2 + y1)
            sum += (x2 - x1) * (y2 + y1);
        }
        
        return (sum >= 0.0) ? WindingOrder.CLOCKWISE : WindingOrder.COUNTER_CLOCKWISE;
    }
    
    // Getters
    
    public List<Point> getPointsList() {
        return points;
    }
    
    public Point[] getPoints() {
        return points.toArray(new Point[0]);
    }
    
    public List<LineSegment> getLineSegmentsList() {
        return lineSegments;
    }
    
    public LineSegment[] getLineSegments() {
        return lineSegments.toArray(new LineSegment[0]);
    }
    
    public Mbr getMbr() {
        return mbr;
    }
    
    public static Point getExternalPoint() {
        return EXTERNAL_POINT;
    }
    
    /**
     * Enum for winding order.
     */
    public enum WindingOrder {
        CLOCKWISE,
        COUNTER_CLOCKWISE
    }
    
    @Override
    public String toString() {
        return String.format("CartesianRing{points=%d, segments=%d}",
                points.size(), lineSegments.size());
    }
}
