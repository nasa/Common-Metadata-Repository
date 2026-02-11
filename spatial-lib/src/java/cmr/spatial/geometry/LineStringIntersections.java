package cmr.spatial.geometry;

import cmr.spatial.internal.arc.Arc;
import cmr.spatial.internal.arc.ArcLineSegmentIntersections;
import cmr.spatial.shape.LineString;
import cmr.spatial.shape.Mbr;
import cmr.spatial.shape.Point;
import cmr.spatial.internal.segment.LineSegment;
import cmr.spatial.math.MathUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements LineString intersection testing.
 * Translates covers-point?, intersects-br?, and intersects-line-string? from Clojure.
 */
public class LineStringIntersections {

    /**
     * Tests if a line string covers a point.
     * A line string covers a point if the point is one of the line's points
     * or if the point lies on one of the line's segments.
     *
     * @param lineString The line string to test
     * @param point The point to check
     * @return true if the line string covers the point
     */
    public static boolean coversPoint(LineString lineString, Point point) {
        String coordSystem = lineString.getCoordinateSystem();
        List<Double> ordinates = lineString.getOrdinates();
        
        // Convert ordinates to points
        List<Point> points = ordinatesToPoints(ordinates);
        
        // Check if point is in the point set
        if (points.contains(point)) {
            return true;
        }
        
        // Check if point lies on any segment
        if ("geodetic".equals(coordSystem)) {
            List<Arc> arcs = pointsToArcs(points);
            for (Arc arc : arcs) {
                if (arc.pointOnArc(point)) {
                    return true;
                }
            }
        } else { // cartesian
            List<LineSegment> segments = pointsToLineSegments(points);
            for (LineSegment segment : segments) {
                if (segment.pointOnSegment(point)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * Tests if a line string intersects a minimal bounding rectangle (MBR).
     */
    public static boolean intersectsMbr(LineString lineString, Mbr mbr) {
        String coordSystem = lineString.getCoordinateSystem();
        List<Double> ordinates = lineString.getOrdinates();
        List<Point> points = ordinatesToPoints(ordinates);
        
        // Calculate line MBR
        Mbr lineMbr = calculateLineMbr(coordSystem, points);
        
        // Quick reject if MBRs don't intersect
        if (!MbrIntersections.mbrsIntersect(coordSystem, lineMbr, mbr)) {
            return false;
        }
        
        // Check if MBR is single point
        if (isSinglePoint(mbr)) {
            Point testPoint = new Point(mbr.getWest(), mbr.getNorth());
            return coversPoint(lineString, testPoint);
        }
        
        // Check if MBR covers any line points
        for (Point p : points) {
            if (mbrCoversPoint(coordSystem, mbr, p)) {
                return true;
            }
        }
        
        // Check if line covers any MBR corner points
        List<Point> corners = mbrCornerPoints(mbr);
        for (Point corner : corners) {
            if (coversPoint(lineString, corner)) {
                return true;
            }
        }
        
        // Check if any segments intersect MBR edges
        List<LineSegment> mbrSegments = LineSegment.mbrToLineSegments(mbr);
        
        if ("geodetic".equals(coordSystem)) {
            List<Arc> arcs = pointsToArcs(points);
            for (Arc arc : arcs) {
                for (LineSegment mbrSeg : mbrSegments) {
                    if (ArcLineSegmentIntersections.intersects(arc, mbrSeg)) {
                        return true;
                    }
                }
            }
        } else { // cartesian
            List<LineSegment> segments = pointsToLineSegments(points);
            for (LineSegment seg : segments) {
                for (LineSegment mbrSeg : mbrSegments) {
                    if (ArcLineSegmentIntersections.intersects(seg, mbrSeg)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }

    /**
     * Tests if two line strings intersect.
     */
    public static boolean intersectsLineString(LineString lineString1, LineString lineString2) {
        List<Point> points1 = ordinatesToPoints(lineString1.getOrdinates());
        List<Point> points2 = ordinatesToPoints(lineString2.getOrdinates());
        
        String coordSys1 = lineString1.getCoordinateSystem();
        String coordSys2 = lineString2.getCoordinateSystem();
        
        // Get segments for both lines
        Object[] segments1 = getSegments(coordSys1, points1);
        Object[] segments2 = getSegments(coordSys2, points2);
        
        // Check all segment pairs
        for (Object seg1 : segments1) {
            for (Object seg2 : segments2) {
                if (ArcLineSegmentIntersections.intersects(seg1, seg2)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    // Helper methods
    
    private static List<Point> ordinatesToPoints(List<Double> ordinates) {
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < ordinates.size(); i += 2) {
            points.add(new Point(ordinates.get(i), ordinates.get(i + 1)));
        }
        return points;
    }
    
    private static List<Arc> pointsToArcs(List<Point> points) {
        List<Arc> arcs = new ArrayList<>();
        for (int i = 0; i < points.size() - 1; i++) {
            arcs.add(Arc.createArc(points.get(i), points.get(i + 1)));
        }
        return arcs;
    }
    
    private static List<LineSegment> pointsToLineSegments(List<Point> points) {
        return LineSegment.pointsToLineSegments(points);
    }
    
    private static Object[] getSegments(String coordSystem, List<Point> points) {
        if ("geodetic".equals(coordSystem)) {
            return pointsToArcs(points).toArray();
        } else {
            return pointsToLineSegments(points).toArray();
        }
    }
    
    private static Mbr calculateLineMbr(String coordSystem, List<Point> points) {
        if (points.isEmpty()) {
            return new Mbr(0, 0, 0, 0);
        }
        
        double west = points.get(0).getLon();
        double east = west;
        double north = points.get(0).getLat();
        double south = north;
        
        for (Point p : points) {
            double lon = p.getLon();
            double lat = p.getLat();
            if (lon < west) west = lon;
            if (lon > east) east = lon;
            if (lat > north) north = lat;
            if (lat < south) south = lat;
        }
        
        return new Mbr(west, north, east, south);
    }
    
    private static boolean isSinglePoint(Mbr mbr) {
        return MathUtils.doubleApprox(mbr.getWest(), mbr.getEast(), MathUtils.DELTA)
            && MathUtils.doubleApprox(mbr.getNorth(), mbr.getSouth(), MathUtils.DELTA);
    }
    
    private static boolean mbrCoversPoint(String coordSystem, Mbr mbr, Point point) {
        double lon = point.getLon();
        double lat = point.getLat();
        
        boolean latInRange = lat >= mbr.getSouth() && lat <= mbr.getNorth();
        if (!latInRange) return false;
        
        if ("geodetic".equals(coordSystem) && mbr.getWest() > mbr.getEast()) {
            // Crosses antimeridian
            return lon >= mbr.getWest() || lon <= mbr.getEast();
        } else {
            return lon >= mbr.getWest() && lon <= mbr.getEast();
        }
    }
    
    private static List<Point> mbrCornerPoints(Mbr mbr) {
        List<Point> corners = new ArrayList<>();
        corners.add(new Point(mbr.getWest(), mbr.getNorth()));  // NW
        corners.add(new Point(mbr.getEast(), mbr.getNorth()));  // NE
        corners.add(new Point(mbr.getEast(), mbr.getSouth()));  // SE
        corners.add(new Point(mbr.getWest(), mbr.getSouth()));  // SW
        return corners;
    }
}
