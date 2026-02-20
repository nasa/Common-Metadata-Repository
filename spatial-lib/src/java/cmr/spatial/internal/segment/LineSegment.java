package cmr.spatial.internal.segment;

import cmr.spatial.shape.Mbr;
import cmr.spatial.shape.Point;
import cmr.spatial.math.MathUtils;
import cmr.spatial.geometry.MbrIntersections;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a cartesian line segment (flat plane geometry).
 * A line segment is defined by two endpoints and has properties like slope and y-intercept.
 * 
 * This class handles special cases:
 * - Vertical lines (undefined slope)
 * - Horizontal lines (zero slope)
 * - Parallel lines (same slope)
 * 
 * For intersections, the intersection point must be within both line segments' MBRs.
 * 
 * Based on cmr.spatial.line-segment Clojure namespace.
 */
public class LineSegment {
    
    /**
     * Tolerance used for determining if points are on the line segment.
     */
    public static final double COVERS_TOLERANCE = 0.00001;
    
    /**
     * Tolerance used in MBR coverage checks (matches cmr.spatial.mbr/COVERS_TOLERANCE).
     */
    public static final double MBR_COVERS_TOLERANCE = 0.0000000001;
    
    /**
     * Tolerance used for the covers method during point intersections.
     * Longitudes and latitudes technically outside the bounding rectangle but within 
     * this tolerance will be considered covered by the bounding rectangle.
     */
    public static final double INTERSECTION_COVERS_TOLERANCE = 0.0000001;
    
    private final Point point1;
    private final Point point2;
    private final boolean vertical;
    private final boolean horizontal;
    private final Double m;  // Slope (can be null for vertical lines)
    private final Double b;  // Y-intercept (can be null for vertical lines)
    private final Mbr mbr;   // Bounding rectangle
    
    /**
     * Private constructor. Use createLineSegment factory method instead.
     */
    private LineSegment(Point point1, Point point2, boolean vertical, boolean horizontal,
                       Double m, Double b, Mbr mbr) {
        this.point1 = point1;
        this.point2 = point2;
        this.vertical = vertical;
        this.horizontal = horizontal;
        this.m = m;
        this.b = b;
        this.mbr = mbr;
    }
    
    /**
     * Creates a new line segment from two points.
     * Calculates slope, y-intercept, and bounding rectangle.
     * 
     * @param p1 First endpoint
     * @param p2 Second endpoint
     * @return A new LineSegment
     */
    public static LineSegment createLineSegment(Point p1, Point p2) {
        double lon1 = p1.getLon();
        double lat1 = p1.getLat();
        double lon2 = p2.getLon();
        double lat2 = p2.getLat();
        
        boolean vertical = (lon1 == lon2);
        boolean horizontal = (lat1 == lat2);
        
        Double m;
        Double b;
        
        if (vertical) {
            m = Double.POSITIVE_INFINITY;
            b = null;  // Undefined for vertical lines
        } else if (horizontal) {
            m = 0.0;
            b = lat1;
        } else {
            m = (lat2 - lat1) / (lon2 - lon1);
            b = lat1 - (m * lon1);
        }
        
        // Create MBR (does not cross antimeridian for cartesian polygons)
        Mbr mbr = pointsToMbr(lon1, lat1, lon2, lat2);
        
        return new LineSegment(p1, p2, vertical, horizontal, m, b, mbr);
    }
    
    /**
     * Creates an MBR that covers both points but does not cross the antimeridian.
     */
    private static Mbr pointsToMbr(double lon1, double lat1, double lon2, double lat2) {
        double north = Math.max(lat1, lat2);
        double south = Math.min(lat1, lat2);
        double west, east;
        
        if (lon2 > lon1) {
            west = lon1;
            east = lon2;
        } else {
            west = lon2;
            east = lon1;
        }
        
        return new Mbr(west, north, east, south);
    }
    
    /**
     * Checks if a point lies on this line segment.
     * Uses tolerance for approximate equality.
     * 
     * @param point The point to test
     * @return true if the point is on the segment (within tolerance)
     */
    public boolean pointOnSegment(Point point) {
        // Check if point equals either endpoint
        if (MathUtils.doubleApprox(point.getLon(), point1.getLon(), COVERS_TOLERANCE) &&
            MathUtils.doubleApprox(point.getLat(), point1.getLat(), COVERS_TOLERANCE)) {
            return true;
        }
        if (MathUtils.doubleApprox(point.getLon(), point2.getLon(), COVERS_TOLERANCE) &&
            MathUtils.doubleApprox(point.getLat(), point2.getLat(), COVERS_TOLERANCE)) {
            return true;
        }
        
        // Check if point is within MBR
        if (!cartesianCoversPoint(mbr, point, COVERS_TOLERANCE)) {
            return false;
        }
        
        // For horizontal lines, check latitude
        if (horizontal) {
            return MathUtils.doubleApprox(point1.getLat(), point.getLat(), COVERS_TOLERANCE);
        }
        
        // For other lines, calculate expected longitude at the point's latitude
        Double expectedLon = latToLon(point.getLat());
        if (expectedLon == null) {
            return false;
        }
        
        return MathUtils.doubleApprox(expectedLon, point.getLon(), COVERS_TOLERANCE);
    }
    
    /**
     * Finds the intersection point between this line segment and another.
     * Returns null if they don't intersect.
     * 
     * @param other The other line segment
     * @return The intersection point, or null if no intersection
     */
    public Point intersection(LineSegment other) {
        boolean ls1Vert = this.vertical;
        boolean ls2Vert = other.vertical;
        boolean ls1Horz = this.horizontal;
        boolean ls2Horz = other.horizontal;
        
        // Handle vertical line cases
        if (ls1Vert) {
            if (ls2Vert) {
                return intersectionBothVertical(this, other);
            } else if (ls2Horz) {
                return intersectionHorizontalAndVertical(other, this);
            } else {
                return intersectionOneVertical(this, other);
            }
        }
        
        if (ls2Vert) {
            if (ls1Horz) {
                return intersectionHorizontalAndVertical(this, other);
            } else {
                return intersectionOneVertical(other, this);
            }
        }
        
        // Handle horizontal line cases
        if (ls1Horz && ls2Horz) {
            return intersectionBothHorizontal(this, other);
        }
        
        // Handle parallel lines (same slope)
        if (this.m.equals(other.m)) {
            return intersectionParallel(this, other);
        }
        
        // Normal intersection
        return intersectionNormal(this, other);
    }
    
    /**
     * Creates intermediate points along the line segment for densification.
     * 
     * @param numPoints Number of intermediate points to create
     * @return List of points including endpoints and intermediate points
     */
    public List<Point> densifyLineSegment(int numPoints) {
        if (numPoints <= 0) {
            throw new IllegalArgumentException("numPoints must be > 0, got: " + numPoints);
        }
        
        List<Point> points = new ArrayList<>();
        
        // For vertical lines, just return endpoints
        if (vertical) {
            points.add(point1);
            points.add(point2);
            return points;
        }
        
        double lon1 = point1.getLon();
        double lat1 = point1.getLat();
        double lon2 = point2.getLon();
        double lat2 = point2.getLat();
        
        // Calculate differences per step
        double lonDiff = (lon2 - lon1) / numPoints;
        double latDiff = (lat2 - lat1) / numPoints;
        
        // Create points
        for (int i = 0; i <= numPoints; i++) {
            double lon = lon1 + (lonDiff * i);
            double lat = lat1 + (latDiff * i);
            points.add(new Point(lon, lat));
        }
        
        return points;
    }
    
    /**
     * Solves the line equation for longitude at a given latitude.
     * For line equation y = mx + b, this returns x = (y - b) / m
     * 
     * @param lat The latitude (y-coordinate)
     * @return The longitude (x-coordinate) at the given latitude, or null if out of bounds
     */
    public Double latToLon(double lat) {
        if (horizontal) {
            throw new IllegalStateException(
                "Cannot determine longitude of points at a given latitude in a horizontal line");
        }
        
        if (vertical) {
            // For vertical lines, check if lat is within range
            if (lat >= mbr.getSouth() && lat <= mbr.getNorth()) {
                return point1.getLon();
            }
            return null;
        }
        
        // Check if latitude is within MBR bounds
        if (lat < mbr.getSouth() || lat > mbr.getNorth()) {
            return null;
        }
        
        // Solve for x: x = (y - b) / m
        return (lat - b) / m;
    }
    
    /**
     * Converts an MBR to a list of line segments representing its edges.
     * 
     * @param mbr The minimum bounding rectangle
     * @return List of line segments forming the MBR edges
     */
    public static List<LineSegment> mbrToLineSegments(Mbr mbr) {
        List<LineSegment> segments = new ArrayList<>();
        
        double west = mbr.getWest();
        double east = mbr.getEast();
        double north = mbr.getNorth();
        double south = mbr.getSouth();
        
        // Check for degenerate cases
        if (west == east && north == south) {
            throw new IllegalArgumentException(
                "This function doesn't work for an MBR that's a single point.");
        }
        
        if (west == east) {
            // Zero width MBR - single vertical line
            segments.add(createLineSegment(
                new Point(west, north),
                new Point(east, south)
            ));
            return segments;
        }
        
        if (north == south) {
            // Zero height MBR - single or two horizontal lines
            if (MbrIntersections.crossesAntimeridian(mbr)) {
                segments.add(createLineSegment(
                    new Point(west, north),
                    new Point(180.0, north)
                ));
                segments.add(createLineSegment(
                    new Point(-180.0, north),
                    new Point(east, north)
                ));
            } else {
                segments.add(createLineSegment(
                    new Point(west, north),
                    new Point(east, south)
                ));
            }
            return segments;
        }
        
        // Normal rectangle
        if (MbrIntersections.crossesAntimeridian(mbr)) {
            // Crosses antimeridian - need 6 segments
            Point ul = new Point(west, north);
            Point ur = new Point(east, north);
            Point lr = new Point(east, south);
            Point ll = new Point(west, south);
            Point rightTop = new Point(180.0, north);
            Point rightBot = new Point(180.0, south);
            Point leftTop = new Point(-180.0, north);
            Point leftBot = new Point(-180.0, south);
            
            segments.add(createLineSegment(ul, rightTop));
            segments.add(createLineSegment(leftTop, ur));
            segments.add(createLineSegment(ur, lr));
            segments.add(createLineSegment(lr, leftBot));
            segments.add(createLineSegment(rightBot, ll));
            segments.add(createLineSegment(ll, ul));
        } else {
            // Normal rectangle - 4 segments
            Point ul = new Point(west, north);
            Point ur = new Point(east, north);
            Point lr = new Point(east, south);
            Point ll = new Point(west, south);
            
            segments.add(createLineSegment(ul, ur));  // Top
            segments.add(createLineSegment(ur, lr));  // Right
            segments.add(createLineSegment(lr, ll));  // Bottom
            segments.add(createLineSegment(ll, ul));  // Left
        }
        
        return segments;
    }
    
    /**
     * Creates line segments connecting consecutive points in a list.
     * 
     * @param points List of points to connect
     * @return List of line segments
     */
    public static List<LineSegment> pointsToLineSegments(List<Point> points) {
        List<LineSegment> segments = new ArrayList<>();
        
        for (int i = 0; i < points.size() - 1; i++) {
            segments.add(createLineSegment(points.get(i), points.get(i + 1)));
        }
        
        return segments;
    }
    
    // ========== Private intersection helper methods ==========
    
    /**
     * Returns the intersection point of two vertical line segments.
     */
    private static Point intersectionBothVertical(LineSegment ls1, LineSegment ls2) {
        Mbr mbr1 = ls1.mbr;
        Mbr mbr2 = ls2.mbr;
        double lon1 = ls1.point1.getLon();
        double lon2 = ls2.point1.getLon();
        
        if (lon1 != lon2) {
            return null;
        }
        
        double ls1North = mbr1.getNorth();
        double ls1South = mbr1.getSouth();
        double ls2North = mbr2.getNorth();
        double ls2South = mbr2.getSouth();
        
        // Check which latitude ranges overlap
        if (MathUtils.withinRange(ls2North, ls1South, ls1North)) {
            return new Point(lon1, ls2North);
        } else if (MathUtils.withinRange(ls2South, ls1South, ls1North)) {
            return new Point(lon1, ls2South);
        } else if (MathUtils.withinRange(ls1South, ls2South, ls2North)) {
            return new Point(lon1, ls1South);
        }
        
        return null;
    }
    
    /**
     * Returns the intersection point of one vertical and one non-vertical line.
     */
    /**
     * Returns the intersection of one vertical line and another non-vertical line.
     * Matches Clojure intersection-one-vertical.
     */
    private static Point intersectionOneVertical(LineSegment vertLs, LineSegment ls) {
        double lon = vertLs.point1.getLon();
        Mbr mbr = ls.mbr;
        Mbr vertMbr = vertLs.mbr;
        
        // Calculate latitude at the vertical line's longitude
        Double lat = ls.lonToLat(lon);
        if (lat == null) {
            return null;
        }
        
        Point point = new Point(lon, lat);
        
        // Check if point is within both MBRs (uses default tolerance, not INTERSECTION_COVERS_TOLERANCE)
        if (cartesianCoversPoint(mbr, point, MBR_COVERS_TOLERANCE) &&
            cartesianCoversPoint(vertMbr, point, MBR_COVERS_TOLERANCE)) {
            return point;
        }
        
        return null;
    }
    
    /**
     * Returns the intersection point of two horizontal line segments.
     */
    private static Point intersectionBothHorizontal(LineSegment ls1, LineSegment ls2) {
        Mbr mbr1 = ls1.mbr;
        Mbr mbr2 = ls2.mbr;
        double lat1 = ls1.point1.getLat();
        double lat2 = ls2.point1.getLat();
        
        if (lat1 != lat2) {
            return null;
        }
        
        double ls1East = mbr1.getEast();
        double ls1West = mbr1.getWest();
        double ls2East = mbr2.getEast();
        double ls2West = mbr2.getWest();
        
        // Check which longitude ranges overlap
        if (MathUtils.withinRange(ls2East, ls1West, ls1East)) {
            return new Point(ls2East, lat1);
        } else if (MathUtils.withinRange(ls2West, ls1West, ls1East)) {
            return new Point(ls2West, lat1);
        } else if (MathUtils.withinRange(ls1West, ls2West, ls2East)) {
            return new Point(ls1West, lat1);
        }
        
        return null;
    }
    
    /**
     * Returns the intersection of one horizontal and one vertical line.
     */
    private static Point intersectionHorizontalAndVertical(LineSegment horizLs, LineSegment vertLs) {
        Mbr horizMbr = horizLs.mbr;
        double horizLat = horizMbr.getNorth();
        double horizWest = horizMbr.getWest();
        double horizEast = horizMbr.getEast();
        
        Mbr vertMbr = vertLs.mbr;
        double vertLon = vertMbr.getWest();
        double vertSouth = vertMbr.getSouth();
        double vertNorth = vertMbr.getNorth();
        
        if (MathUtils.withinRange(horizLat, vertSouth, vertNorth) &&
            MathUtils.withinRange(vertLon, horizWest, horizEast)) {
            return new Point(vertLon, horizLat);
        }
        
        return null;
    }
    
    /**
     * Returns the intersection of two parallel line segments.
     */
    private static Point intersectionParallel(LineSegment ls1, LineSegment ls2) {
        // They only intersect if y-intercepts are the same
        if (!ls1.b.equals(ls2.b)) {
            return null;
        }
        
        // Find the common intersecting MBR
        Mbr intersectionMbr = mbrIntersection(ls1.mbr, ls2.mbr);
        if (intersectionMbr == null) {
            return null;
        }
        
        // Use the longitude to find a point
        double lon = intersectionMbr.getWest();
        Double lat = ls1.lonToLat(lon);
        
        if (lat != null) {
            return new Point(lon, lat);
        }
        
        return null;
    }
    
    /**
     * Returns the intersection of two normal (non-vertical, non-horizontal, non-parallel) line segments.
     */
    private static Point intersectionNormal(LineSegment ls1, LineSegment ls2) {
        double m1 = ls1.m;
        double b1 = ls1.b;
        double m2 = ls2.m;
        double b2 = ls2.b;
        Mbr mbr1 = ls1.mbr;
        Mbr mbr2 = ls2.mbr;
        
        // Solve for intersection: m1*x + b1 = m2*x + b2
        // x = (b2 - b1) / (m1 - m2)
        double lon = (b2 - b1) / (m1 - m2);
        double lat = m1 * lon + b1;
        
        Point point = new Point(lon, lat);
        
        // Check if intersection point is within both MBRs
        if (cartesianCoversPoint(mbr1, point, INTERSECTION_COVERS_TOLERANCE) &&
            cartesianCoversPoint(mbr2, point, INTERSECTION_COVERS_TOLERANCE)) {
            return point;
        }
        
        return null;
    }
    
    /**
     * Returns the latitude at the given longitude for this line segment.
     */
    private Double lonToLat(double lon) {
        if (vertical) {
            throw new IllegalStateException(
                "Cannot determine latitude of points at a given longitude in a vertical line");
        }
        
        // Check if longitude is within MBR bounds (with tolerance)
        if (!cartesianLonRangeCoversLon(mbr.getWest(), mbr.getEast(), lon, COVERS_TOLERANCE)) {
            return null;
        }
        
        // Calculate: y = mx + b
        return m * lon + b;
    }
    
    /**
     * Checks if a cartesian longitude range covers a specific longitude.
     */
    private static boolean cartesianLonRangeCoversLon(double west, double east, double lon, double tolerance) {
        return lon >= (west - tolerance) && lon <= (east + tolerance);
    }
    
    /**
     * Checks if an MBR covers a point in cartesian coordinates.
     * Matches Clojure cartesian-covers-point? with tolerance.
     */
    private static boolean cartesianCoversPoint(Mbr mbr, Point point, double tolerance) {
        double lon = point.getLon();
        double lat = point.getLat();
        
        // Check latitude (covers-lat?)
        double north = mbr.getNorth() + tolerance;
        double south = mbr.getSouth() - tolerance;
        if (lat < south || lat > north) {
            return false;
        }
        
        // Check longitude (cartesian-lon-range-covers-lon?)
        double west = mbr.getWest() - tolerance;
        double east = mbr.getEast() + tolerance;
        boolean crossesAntimeridian = west > east;  // Check AFTER adjusting
        
        if (crossesAntimeridian) {
            return lon >= west || lon <= east;
        } else {
            return lon >= west && lon <= east;
        }
    }
    
    /**
     * Returns the intersection of two MBRs, or null if they don't intersect.
     */
    private static Mbr mbrIntersection(Mbr mbr1, Mbr mbr2) {
        if (!MbrIntersections.mbrsIntersect("cartesian", mbr1, mbr2)) {
            return null;
        }
        
        double west = Math.max(mbr1.getWest(), mbr2.getWest());
        double east = Math.min(mbr1.getEast(), mbr2.getEast());
        double north = Math.min(mbr1.getNorth(), mbr2.getNorth());
        double south = Math.max(mbr1.getSouth(), mbr2.getSouth());
        
        return new Mbr(west, north, east, south);
    }
    
    // ========== Getters ==========
    
    public Point getPoint1() {
        return point1;
    }
    
    public Point getPoint2() {
        return point2;
    }
    
    public boolean isVertical() {
        return vertical;
    }
    
    public boolean isHorizontal() {
        return horizontal;
    }
    
    public Double getM() {
        return m;
    }
    
    public Double getB() {
        return b;
    }
    
    public Mbr getMbr() {
        return mbr;
    }
    
    @Override
    public String toString() {
        return String.format("LineSegment{p1=%s, p2=%s, vertical=%b, horizontal=%b, m=%s, b=%s}",
                           point1, point2, vertical, horizontal, m, b);
    }
}
