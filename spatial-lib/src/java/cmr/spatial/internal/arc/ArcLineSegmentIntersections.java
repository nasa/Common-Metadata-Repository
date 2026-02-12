package cmr.spatial.internal.arc;

import cmr.spatial.shape.Mbr;
import cmr.spatial.shape.Point;
import cmr.spatial.internal.segment.LineSegment;
import cmr.spatial.math.CoordinateConversion;
import cmr.spatial.math.MathUtils;
import cmr.spatial.math.Vector;
import cmr.spatial.geometry.MbrIntersections;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides intersection functions for finding the intersection of spherical arcs and cartesian line segments.
 * 
 * This class handles the complex problem of intersecting geodetic (spherical) arcs with
 * cartesian (flat plane) line segments. The key challenge is that these geometries exist
 * in different coordinate systems.
 * 
 * Key algorithms:
 * - Arc-Arc: Great circle plane intersection using cross product
 * - LineSegment-LineSegment: Cartesian line equation solving
 * - Arc-LineSegment (mixed): Densification strategy to bridge coordinate systems
 * 
 * Based on cmr.spatial.arc-line-segment-intersections Clojure namespace.
 */
public class ArcLineSegmentIntersections {
    
    /**
     * Tolerance for approximate equality comparisons.
     */
    private static final double APPROXIMATION_DELTA = Arc.APPROXIMATION_DELTA;
    
    /**
     * Default densification distance in degrees for converting line segments to arcs.
     */
    private static final double DEFAULT_DENSIFICATION_DISTANCE = 0.1;
    
    /**
     * North pole point.
     */
    public static final Point NORTH_POLE = new Point(0.0, 90.0);
    
    /**
     * South pole point.
     */
    public static final Point SOUTH_POLE = new Point(0.0, -90.0);
    
    // ==================== Arc-Arc Intersections ====================
    
    /**
     * Returns the intersection points of two arcs.
     * 
     * Algorithm:
     * 1. Check if MBRs intersect (early exit if not)
     * 2. Handle special case: arcs on the same great circle
     * 3. Calculate plane vector cross product to find intersection line
     * 4. Two antipodal points lie on this intersection line
     * 5. Filter points within both arcs' bounding rectangles
     * 
     * @param a1 First arc
     * @param a2 Second arc
     * @return List of intersection points (0, 1, or 2 points)
     */
    public static List<Point> arcArcIntersections(Arc a1, Arc a2) {
        List<Point> result = new ArrayList<>();
        
        // Check if any of the arc MBRs intersect
        if (!arcMbrsIntersect(a1, a2)) {
            return result;
        }
        
        GreatCircle gc1 = a1.getGreatCircle();
        GreatCircle gc2 = a2.getGreatCircle();
        
        // Special case: both arcs on the same great circle
        if (gc1.isEquivalent(gc2)) {
            return greatCircleEquivalencyArcIntersections(a1, a2);
        }
        
        // Default case: compute intersection using cross product
        Vector pv1 = gc1.getPlaneVector();
        Vector pv2 = gc2.getPlaneVector();
        
        // Compute the great circle intersection vector
        // This is the cross product of the vectors defining the great circle planes
        Vector intersectionVector = pv1.crossProduct(pv2);
        
        // Convert to points (two antipodal points on the intersection line)
        Point intersectionPoint1 = CoordinateConversion.vectorToPoint(intersectionVector);
        Point intersectionPoint2 = antipodal(intersectionPoint1);
        
        // Check if points are within bounding rectangles of both arcs
        boolean point1Within = pointWithinArcBoundingRectangles(intersectionPoint1, a1, a2);
        boolean point2Within = pointWithinArcBoundingRectangles(intersectionPoint2, a1, a2);
        
        if (point1Within) {
            result.add(intersectionPoint1);
        }
        if (point2Within) {
            result.add(intersectionPoint2);
        }
        
        return result;
    }
    
    /**
     * Special case arc intersections for both arcs having the same great circle.
     * Returns the endpoint intersections that fall within both arcs' bounds.
     */
    private static List<Point> greatCircleEquivalencyArcIntersections(Arc a1, Arc a2) {
        List<Point> result = new ArrayList<>();
        Set<Point> points = new HashSet<>();
        
        // Collect all endpoints
        points.add(a1.getWestPoint());
        points.add(a1.getEastPoint());
        points.add(a2.getWestPoint());
        points.add(a2.getEastPoint());
        
        // Filter points that are within both arcs' bounding rectangles
        for (Point p : points) {
            if (pointWithinArcBoundingRectangles(p, a1, a2)) {
                result.add(p);
            }
        }
        
        return result;
    }
    
    /**
     * Returns true if any of the arc MBRs intersect.
     */
    private static boolean arcMbrsIntersect(Arc a1, Arc a2) {
        Mbr[] a1Mbrs = a1.getMbrs();
        Mbr[] a2Mbrs = a2.getMbrs();
        
        for (Mbr mbr1 : a1Mbrs) {
            for (Mbr mbr2 : a2Mbrs) {
                if (MbrIntersections.mbrsIntersect("geodetic", mbr1, mbr2)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Returns true if the point is within the bounding rectangles of both arcs.
     */
    private static boolean pointWithinArcBoundingRectangles(Point p, Arc a1, Arc a2) {
        boolean inA1 = false;
        boolean inA2 = false;
        
        for (Mbr mbr : a1.getMbrs()) {
            if (geodeticCoversPoint(mbr, p)) {
                inA1 = true;
                break;
            }
        }
        
        for (Mbr mbr : a2.getMbrs()) {
            if (geodeticCoversPoint(mbr, p)) {
                inA2 = true;
                break;
            }
        }
        
        return inA1 && inA2;
    }
    
    // ==================== LineSegment-LineSegment Intersections ====================
    
    /**
     * Returns the intersection points of two line segments.
     * 
     * Handles all special cases:
     * - Both vertical: check longitude match and latitude overlap
     * - Both horizontal: check latitude match and longitude overlap
     * - One vertical, one horizontal: single point intersection
     * - Parallel lines (same slope): check if on same line
     * - Normal case: solve m1*x + b1 = m2*x + b2
     * 
     * @param ls1 First line segment
     * @param ls2 Second line segment
     * @return List of intersection points (0 or 1 point)
     */
    public static List<Point> lineSegmentLineSegmentIntersections(LineSegment ls1, LineSegment ls2) {
        List<Point> result = new ArrayList<>();
        Point intersection = ls1.intersection(ls2);
        if (intersection != null) {
            result.add(intersection);
        }
        return result;
    }
    
    // ==================== Arc-LineSegment Intersections ====================
    
    /**
     * Returns the intersection points of an arc and a line segment.
     * 
     * This is the most complex case as it involves geodetic (arc) and cartesian (line segment)
     * coordinate systems. Strategy:
     * 
     * 1. Pre-check: Do MBRs intersect? (early exit if not)
     * 2. Handle pole intersections
     * 3. Vertical line segment: convert to arc, use arc-arc intersection
     * 4. Horizontal line segment: use arc.pointsAtLat() method
     * 5. Vertical arc: convert to line segments at pole, use line-line intersection
     * 6. General case: densify line segment, convert to arcs, find arc-arc intersections
     * 
     * @param arc The arc
     * @param ls The line segment
     * @return List of intersection points
     */
    public static List<Point> arcLineSegmentIntersections(Arc arc, LineSegment ls) {
        List<Point> result = new ArrayList<>();
        
        Mbr lsMbr = ls.getMbr();
        Mbr[] arcMbrs = arc.getMbrs();
        
        // Check if MBRs intersect
        boolean mbrsIntersect = false;
        List<Mbr> intersectingMbrs = new ArrayList<>();
        
        for (Mbr arcMbr : arcMbrs) {
            if (MbrIntersections.mbrsIntersect("geodetic", lsMbr, arcMbr)) {
                mbrsIntersect = true;
                intersectingMbrs.add(arcMbr);
            }
        }
        
        if (!mbrsIntersect) {
            return result;
        }
        
        Point lsPoint1 = ls.getPoint1();
        Point lsPoint2 = ls.getPoint2();
        Point arcWest = arc.getWestPoint();
        Point arcEast = arc.getEastPoint();
        
        // Handle pole intersections
        if ((isNorthPole(arcWest) || isNorthPole(arcEast)) &&
            (isNorthPole(lsPoint1) || isNorthPole(lsPoint2))) {
            result.add(NORTH_POLE);
        }
        
        if ((isSouthPole(arcWest) || isSouthPole(arcEast)) &&
            (isSouthPole(lsPoint1) || isSouthPole(lsPoint2))) {
            result.add(SOUTH_POLE);
        }
        
        // Vertical line segment: treat as a vertical arc
        if (ls.isVertical()) {
            try {
                Arc lsArc = Arc.createArc(lsPoint1, lsPoint2);
                result.addAll(arcArcIntersections(arc, lsArc));
            } catch (IllegalArgumentException e) {
                // Handle degenerate endpoints
                if (lsPoint1.equals(lsPoint2)) {
                    // Line segment is a single point - check if it's on the arc
                    if (arc.pointOnArc(lsPoint1)) {
                        result.add(lsPoint1);
                    }
                } else {
                    // Antipodal points: the segment spans a full meridian
                    // This is complex - for now, return empty (could be improved)
                }
            }
            return removeDuplicatePoints(result);
        }
        
        // Horizontal line segment: use arc latitude segment intersection
        if (ls.isHorizontal()) {
            double lat = lsPoint1.getLat();
            double lon1 = lsPoint1.getLon();
            double lon2 = lsPoint2.getLon();
            double west = Math.min(lon1, lon2);
            double east = Math.max(lon1, lon2);
            
            result.addAll(latSegmentIntersections(arc, lat, west, east));
            return removeDuplicatePoints(result);
        }
        
        // Vertical arc: convert to line segments
        if (arc.isVertical()) {
            result.addAll(verticalArcLineSegmentIntersections(arc, ls));
            return removeDuplicatePoints(result);
        }
        
        // General case: densify line segment and convert to arcs
        result.addAll(lineSegmentArcIntersectionsWithDensification(ls, arc, intersectingMbrs));
        return removeDuplicatePoints(result);
    }
    
    /**
     * Returns the points where an arc intersects a latitude segment.
     * The latitude segment is defined at lat between lon-west and lon-east.
     */
    private static List<Point> latSegmentIntersections(Arc arc, double lat, double lonWest, double lonEast) {
        List<Point> result = new ArrayList<>();
        
        // Find the points where the arc crosses that latitude (if any)
        Point[] points = arc.pointsAtLat(lat);
        if (points == null) {
            return result;
        }
        
        // Create MBR for the latitude segment
        Mbr latSegMbr = new Mbr(lonWest, lat, lonEast, lat);
        Mbr[] arcMbrs = arc.getMbrs();
        
        // Filter points that are within the lat segment and arc MBRs
        for (Point p : points) {
            if (geodeticCoversPoint(latSegMbr, p)) {
                for (Mbr arcMbr : arcMbrs) {
                    if (geodeticCoversPoint(arcMbr, p)) {
                        result.add(p);
                        break;
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * Determines the intersection points of a vertical arc and a line segment.
     * Converts the arc into equivalent line segments (handling pole crossings).
     */
    private static List<Point> verticalArcLineSegmentIntersections(Arc arc, LineSegment ls) {
        List<Point> result = new ArrayList<>();
        
        Point westPoint = arc.getWestPoint();
        Point eastPoint = arc.getEastPoint();
        List<LineSegment> arcSegments = new ArrayList<>();
        
        // A vertical arc could cross a pole. It gets divided in half at the pole in that case.
        if (arc.crossesNorthPole()) {
            arcSegments.add(LineSegment.createLineSegment(
                westPoint, new Point(westPoint.getLon(), 90.0)));
            arcSegments.add(LineSegment.createLineSegment(
                eastPoint, new Point(eastPoint.getLon(), 90.0)));
        } else if (arc.crossesSouthPole()) {
            arcSegments.add(LineSegment.createLineSegment(
                westPoint, new Point(westPoint.getLon(), -90.0)));
            arcSegments.add(LineSegment.createLineSegment(
                eastPoint, new Point(eastPoint.getLon(), -90.0)));
        } else if (isNorthPole(eastPoint)) {
            // Create a vertical line segment ignoring the original point2 lon
            arcSegments.add(LineSegment.createLineSegment(
                westPoint, new Point(westPoint.getLon(), 90.0)));
        } else if (isNorthPole(westPoint)) {
            // Create a vertical line segment ignoring the original point1 lon
            arcSegments.add(LineSegment.createLineSegment(
                eastPoint, new Point(eastPoint.getLon(), 90.0)));
        } else if (isSouthPole(eastPoint)) {
            arcSegments.add(LineSegment.createLineSegment(
                westPoint, new Point(westPoint.getLon(), -90.0)));
        } else if (isSouthPole(westPoint)) {
            arcSegments.add(LineSegment.createLineSegment(
                eastPoint, new Point(eastPoint.getLon(), -90.0)));
        } else {
            arcSegments.add(LineSegment.createLineSegment(westPoint, eastPoint));
        }
        
        // Find intersections with each segment
        for (LineSegment arcSeg : arcSegments) {
            Point intersection = ls.intersection(arcSeg);
            if (intersection != null) {
                result.add(intersection);
            }
        }
        
        return result;
    }
    
    /**
     * Performs the intersection between a line segment and an arc using densification.
     * 
     * Densification strategy:
     * 1. Subselect line segment to only portions within arc MBRs
     * 2. Densify those portions into many points
     * 3. Convert consecutive points into arcs
     * 4. Find arc-arc intersections
     */
    private static List<Point> lineSegmentArcIntersectionsWithDensification(
            LineSegment ls, Arc arc, List<Mbr> intersectingMbrs) {
        List<Point> result = new ArrayList<>();
        
        // Compute the intersections of the intersecting MBRs
        List<Mbr> mbrIntersections = new ArrayList<>();
        Mbr lsMbr = ls.getMbr();
        
        for (Mbr arcMbr : intersectingMbrs) {
            Mbr intersection = computeMbrIntersection(lsMbr, arcMbr);
            if (intersection != null) {
                mbrIntersections.add(intersection);
            }
        }
        
        // For each intersecting MBR, subselect the line segment and densify
        for (Mbr mbr : mbrIntersections) {
            // Simple subselection: check if endpoints are in MBR
            Point p1 = ls.getPoint1();
            Point p2 = ls.getPoint2();
            
            boolean p1In = cartesianCoversPoint(mbr, p1);
            boolean p2In = cartesianCoversPoint(mbr, p2);
            
            LineSegment segmentToProcess = ls;
            
            // If only one point is in, we might need to clip
            // For simplicity, we'll process the whole segment if any part intersects
            if (!p1In && !p2In) {
                // Neither point in MBR, but MBR intersects - need to find intersection points
                // This is complex, so we'll just densify the whole segment
            }
            
            // Densify the line segment
            List<Point> densifiedPoints = densifyLineSegment(segmentToProcess, DEFAULT_DENSIFICATION_DISTANCE);
            
            // Convert to arcs
            for (int i = 0; i < densifiedPoints.size() - 1; i++) {
                Point arcP1 = densifiedPoints.get(i);
                Point arcP2 = densifiedPoints.get(i + 1);
                
                // Skip if points are equal or too close
                if (pointsApproxEqual(arcP1, arcP2, APPROXIMATION_DELTA)) {
                    continue;
                }
                
                // Try to create arc and find intersections
                try {
                    Arc densifiedArc = Arc.createArc(arcP1, arcP2);
                    List<Point> arcIntersections = arcArcIntersections(arc, densifiedArc);
                    result.addAll(arcIntersections);
                } catch (IllegalArgumentException e) {
                    // Skip if arc creation fails (antipodal points, etc.)
                }
            }
            
            // Also check if any densified points lie on the arc
            for (Point p : densifiedPoints) {
                if (arc.pointOnArc(p)) {
                    result.add(p);
                }
            }
        }
        
        // Remove duplicates
        return removeDuplicatePoints(result);
    }
    
    /**
     * Computes the intersection of two MBRs.
     * Returns null if they don't intersect.
     */
    private static Mbr computeMbrIntersection(Mbr mbr1, Mbr mbr2) {
        if (!MbrIntersections.mbrsIntersect("cartesian", mbr1, mbr2)) {
            return null;
        }
        
        double west = Math.max(mbr1.getWest(), mbr2.getWest());
        double east = Math.min(mbr1.getEast(), mbr2.getEast());
        double north = Math.min(mbr1.getNorth(), mbr2.getNorth());
        double south = Math.max(mbr1.getSouth(), mbr2.getSouth());
        
        return new Mbr(west, north, east, south);
    }
    
    /**
     * Returns points along the line segment for approximating the segment.
     * Does no densification for vertical lines.
     */
    private static List<Point> densifyLineSegment(LineSegment ls, double densificationDist) {
        List<Point> result = new ArrayList<>();
        
        if (ls.isVertical()) {
            result.add(ls.getPoint1());
            result.add(ls.getPoint2());
            return result;
        }
        
        Point p1 = ls.getPoint1();
        Point p2 = ls.getPoint2();
        double lon1 = p1.getLon();
        double lat1 = p1.getLat();
        double lon2 = p2.getLon();
        double lat2 = p2.getLat();
        
        double m = ls.getM();
        
        // Convert slope to angle
        double angleA = Math.atan(m);
        
        // Calculate the difference to add for each point
        double latDiff = densificationDist * Math.sin(angleA);
        double lonDiff = densificationDist * Math.cos(angleA);
        
        // If the line is going backwards, flip the signs
        if (lon1 > lon2) {
            latDiff = -latDiff;
            lonDiff = -lonDiff;
        }
        
        // Calculate distance
        double distance = Math.sqrt(Math.pow(lat2 - lat1, 2) + Math.pow(lon2 - lon1, 2));
        int numPoints = (int) Math.floor(distance / densificationDist);
        
        // Generate points
        for (int i = 0; i <= numPoints; i++) {
            double lon = lon1 + (lonDiff * i);
            double lat = lat1 + (latDiff * i);
            result.add(new Point(lon, lat));
        }
        
        // Ensure endpoint is included
        if (!pointsApproxEqual(result.get(result.size() - 1), p2, APPROXIMATION_DELTA)) {
            result.add(p2);
        }
        
        return result;
    }
    
    // ==================== Polymorphic Dispatcher ====================
    
    /**
     * Determines if line 1 and line 2 intersect.
     * A line can be an arc or a line segment.
     * 
     * @param seg1 First line (Arc or LineSegment)
     * @param seg2 Second line (Arc or LineSegment)
     * @return List of intersection points
     */
    public static List<Point> intersections(Object seg1, Object seg2) {
        if (seg1 == null || seg2 == null) {
            if (seg1 == null && seg2 == null) {
                throw new IllegalArgumentException("seg1 and seg2 cannot be null");
            } else if (seg1 == null) {
                throw new IllegalArgumentException("seg1 cannot be null");
            } else {
                throw new IllegalArgumentException("seg2 cannot be null");
            }
        }
        if (seg1 instanceof Arc && seg2 instanceof Arc) {
            return arcArcIntersections((Arc) seg1, (Arc) seg2);
        } else if (seg1 instanceof LineSegment && seg2 instanceof LineSegment) {
            return lineSegmentLineSegmentIntersections((LineSegment) seg1, (LineSegment) seg2);
        } else if (seg1 instanceof Arc && seg2 instanceof LineSegment) {
            return arcLineSegmentIntersections((Arc) seg1, (LineSegment) seg2);
        } else if (seg1 instanceof LineSegment && seg2 instanceof Arc) {
            return arcLineSegmentIntersections((Arc) seg2, (LineSegment) seg1);
        } else {
            throw new IllegalArgumentException(
                "Arguments must be Arc or LineSegment, got: " + 
                seg1.getClass().getName() + " and " + seg2.getClass().getName());
        }
    }
    
    /**
     * Returns true if line1 intersects line2.
     */
    public static boolean intersects(Object seg1, Object seg2) {
        return !intersections(seg1, seg2).isEmpty();
    }
    
    // ==================== Utility Methods ====================
    
    /**
     * Returns true if point is at or very near the north pole.
     */
    private static boolean isNorthPole(Point p) {
        return MathUtils.doubleApprox(p.getLat(), 90.0, APPROXIMATION_DELTA);
    }
    
    /**
     * Returns true if point is at or very near the south pole.
     */
    private static boolean isSouthPole(Point p) {
        return MathUtils.doubleApprox(p.getLat(), -90.0, APPROXIMATION_DELTA);
    }
    
    /**
     * Returns the point antipodal (opposite side of sphere) to the given point.
     */
    private static Point antipodal(Point p) {
        double lon = p.getLon();
        double lat = p.getLat();
        
        // Antipodal longitude: add 180 degrees (wrapping to [-180, 180])
        double antipodalLon = lon + 180.0;
        if (antipodalLon > 180.0) {
            antipodalLon -= 360.0;
        }
        
        // Antipodal latitude: negate
        double antipodalLat = -lat;
        
        return new Point(antipodalLon, antipodalLat);
    }
    
    /**
     * Returns true if two points are approximately equal within the given delta.
     */
    private static boolean pointsApproxEqual(Point p1, Point p2, double delta) {
        if (!MathUtils.doubleApprox(p1.getLat(), p2.getLat(), delta)) {
            return false;
        }
        
        if (MathUtils.doubleApprox(p1.getLon(), p2.getLon(), delta)) {
            return true;
        }
        
        // Check if both on antimeridian
        if (Math.abs(p1.getLon()) == 180.0 && Math.abs(p2.getLon()) == 180.0) {
            return true;
        }
        
        // Check if both at poles
        if (isNorthPole(p1) && isNorthPole(p2)) {
            return true;
        }
        if (isSouthPole(p1) && isSouthPole(p2)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Returns true if MBR covers the given point (geodetic coordinates).
     */
    private static boolean geodeticCoversPoint(Mbr mbr, Point p) {
        double tolerance = MathUtils.COVERS_TOLERANCE;
        double lat = p.getLat();
        
        // Handle poles
        if (isNorthPole(p)) {
            return coversLat(mbr, 90.0, tolerance);
        }
        if (isSouthPole(p)) {
            return coversLat(mbr, -90.0, tolerance);
        }
        
        return coversLat(mbr, lat, tolerance) && coversLonGeodetic(mbr, p.getLon(), tolerance);
    }
    
    /**
     * Returns true if MBR covers the given latitude.
     */
    private static boolean coversLat(Mbr mbr, double lat, double tolerance) {
        double north = mbr.getNorth() + tolerance;
        double south = mbr.getSouth() - tolerance;
        return lat >= south && lat <= north;
    }
    
    /**
     * Returns true if MBR covers the given longitude (geodetic).
     * Handles antimeridian crossing.
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
     * Returns true if MBR covers point in cartesian coordinates.
     */
    private static boolean cartesianCoversPoint(Mbr mbr, Point point) {
        double lon = point.getLon();
        double lat = point.getLat();
        
        return lon >= mbr.getWest() && lon <= mbr.getEast() &&
               lat >= mbr.getSouth() && lat <= mbr.getNorth();
    }
    
    /**
     * Removes duplicate points from a list based on approximate equality.
     */
    private static List<Point> removeDuplicatePoints(List<Point> points) {
        List<Point> result = new ArrayList<>();
        
        for (Point p : points) {
            boolean isDuplicate = false;
            for (Point existing : result) {
                if (pointsApproxEqual(p, existing, APPROXIMATION_DELTA)) {
                    isDuplicate = true;
                    break;
                }
            }
            if (!isDuplicate) {
                result.add(p);
            }
        }
        
        return result;
    }
}
