package cmr.spatial.internal.ring;

import cmr.spatial.shape.Point;
import cmr.spatial.shape.Mbr;
import cmr.spatial.internal.arc.Arc;
import cmr.spatial.internal.arc.ArcLineSegmentIntersections;
import cmr.spatial.math.MathUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a geodetic (spherical) ring - a closed sequence of points on a sphere
 * connected by great circle arcs.
 * 
 * Used for geodetic polygons. Points must be in counter-clockwise order when viewed
 * from above the surface. The last point must match the first point to close the ring.
 * 
 * This class handles:
 * - Ray-casting algorithm for point-in-ring tests
 * - Pole containment (rings can contain north/south poles)
 * - Multiple external points for antipodal safety
 * - Antimeridian crossing
 * 
 * Based on cmr.spatial.geodetic-ring Clojure namespace.
 */
public class GeodeticRing {
    
    private static final double EXTERNAL_POINT_PRECISION = 4.0;
    private static final double INTERSECTION_POINT_PRECISION = 5.0;  // Matches Clojure
    private static final double APPROXIMATION_DELTA = Arc.APPROXIMATION_DELTA;
    private static final double MBR_COVERS_TOLERANCE = 0.0000000001;  // Matches Clojure mbr/COVERS_TOLERANCE
    
    private final List<Point> points;
    private final List<Arc> arcs;
    private final Mbr mbr;
    private final List<Point> externalPoints;
    private final boolean containsNorthPole;
    private final boolean containsSouthPole;
    
    /**
     * Private constructor. Use createRing() factory method instead.
     */
    private GeodeticRing(List<Point> points, List<Arc> arcs, Mbr mbr,
                          List<Point> externalPoints, boolean containsNorthPole, 
                          boolean containsSouthPole) {
        this.points = points;
        this.arcs = arcs;
        this.mbr = mbr;
        this.externalPoints = externalPoints;
        this.containsNorthPole = containsNorthPole;
        this.containsSouthPole = containsSouthPole;
    }
    
    /**
     * Creates a geodetic ring from a list of points.
     * Calculates derived fields: arcs, MBR, external points, pole containment.
     * 
     * @param points List of points forming the ring (last must equal first)
     *               Can be either cmr.spatial.shape.Point or cmr.spatial.point.Point (Clojure records)
     * @return A new GeodeticRing
     */
    public static GeodeticRing createRing(List<?> points) {
        // Convert Clojure Points to Java Points if necessary
        List<Point> javaPoints = convertToJavaPoints(points);
        List<Arc> arcs = pointsToArcs(javaPoints);
        
        // Calculate course rotation direction and pole containment
        RotationDirection courseRotation = calculateRotationDirection(arcs);
        boolean containsNorthPole = determineNorthPoleContainment(javaPoints, arcs, courseRotation);
        boolean containsSouthPole = determineSouthPoleContainment(javaPoints, arcs, courseRotation);
        
        // Calculate MBR
        Mbr mbr = calculateMbr(arcs, containsNorthPole, containsSouthPole, javaPoints);
        
        // Calculate external points
        List<Point> externalPoints = calculateExternalPoints(mbr, containsNorthPole, containsSouthPole);
        
        return new GeodeticRing(javaPoints, arcs, mbr, externalPoints, 
                                 containsNorthPole, containsSouthPole);
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
     * Converts a list of points to a list of arcs.
     */
    private static List<Arc> pointsToArcs(List<Point> points) {
        List<Arc> arcs = new ArrayList<>();
        for (int i = 0; i < points.size() - 1; i++) {
            Point p1 = points.get(i);
            Point p2 = points.get(i + 1);
            
            // Skip creating arc if points are equal (would throw exception)
            if (!pointsEqual(p1, p2)) {
                try {
                    arcs.add(Arc.createArc(p1, p2));
                } catch (IllegalArgumentException e) {
                    // Skip antipodal points or other invalid arcs
                }
            }
        }
        return arcs;
    }
    
    /**
     * Calculates the rotation direction of the ring.
     */
    private static RotationDirection calculateRotationDirection(List<Arc> arcs) {
        if (arcs.isEmpty()) {
            return RotationDirection.NONE;
        }
        
        // Collect all initial and ending courses from each arc
        List<Double> courses = new ArrayList<>();
        for (Arc arc : arcs) {
            courses.add(arc.getInitialCourse());
            courses.add(arc.getEndingCourse());
        }
        
        // Add the first course again to complete the turn
        courses.add(courses.get(0));
        
        // Calculate net bearing change by summing angle deltas
        double netTurn = 0.0;
        for (int i = 0; i < courses.size() - 1; i++) {
            netTurn += angleDelta(courses.get(i), courses.get(i + 1));
        }
        
        // Counter-clockwise: ~360 degrees, Clockwise: ~-360 degrees, None: ~0 degrees
        if (Math.abs(netTurn) < 0.01) {
            return RotationDirection.NONE;
        } else if (netTurn > 0.0) {
            return RotationDirection.COUNTER_CLOCKWISE;
        } else {
            return RotationDirection.CLOCKWISE;
        }
    }
    
    /**
     * Find the difference between a pair of angles (in degrees).
     * Matches the Clojure angle-delta function.
     */
    private static double angleDelta(double a1, double a2) {
        double a2Shifted = a2;
        
        // Shift angle 2 so it is always greater than angle 1
        if (a2 < a1) {
            a2Shifted = a2 + 360.0;
        }
        
        // Calculate left turn amount
        double leftTurn = a2Shifted - a1;
        
        // Determine which is smaller: turning left or turning right
        if (leftTurn == 180.0) {
            // Can't determine which is smaller, return 0
            return 0.0;
        } else if (leftTurn > 180.0) {
            // Turning right is smaller (returns negative value)
            return leftTurn - 360.0;
        } else {
            return leftTurn;
        }
    }
    
    /**
     * Determines if the ring contains the north pole.
     */
    private static boolean determineNorthPoleContainment(List<Point> points, List<Arc> arcs, 
                                                         RotationDirection rotation) {
        // Check if any point is the north pole
        for (Point p : points) {
            if (isNorthPole(p)) {
                return true;
            }
        }
        
        // Check if any arc crosses the north pole
        for (Arc arc : arcs) {
            if (arc.crossesNorthPole()) {
                return true;
            }
        }
        
        // If rotation is NONE, check longitude rotation direction
        if (rotation == RotationDirection.NONE) {
            RotationDirection lonRotation = calculateLongitudeRotation(points);
            return lonRotation == RotationDirection.COUNTER_CLOCKWISE;
        }
        
        return false;
    }
    
    /**
     * Determines if the ring contains the south pole.
     */
    private static boolean determineSouthPoleContainment(List<Point> points, List<Arc> arcs,
                                                         RotationDirection rotation) {
        // Check if any point is the south pole
        for (Point p : points) {
            if (isSouthPole(p)) {
                return true;
            }
        }
        
        // Check if any arc crosses the south pole
        for (Arc arc : arcs) {
            if (arc.crossesSouthPole()) {
                return true;
            }
        }
        
        // If rotation is NONE, check longitude rotation direction
        if (rotation == RotationDirection.NONE) {
            RotationDirection lonRotation = calculateLongitudeRotation(points);
            return lonRotation == RotationDirection.CLOCKWISE;
        }
        
        return false;
    }
    
    /**
     * Calculates the rotation direction based on longitudes only.
     */
    private static RotationDirection calculateLongitudeRotation(List<Point> points) {
        if (points.size() < 2) {
            return RotationDirection.NONE;
        }
        
        // Extract longitudes
        List<Double> longitudes = new ArrayList<>();
        for (Point p : points) {
            longitudes.add(p.getLon());
        }
        
        // Calculate net rotation using angle deltas
        double netTurn = 0.0;
        for (int i = 0; i < longitudes.size() - 1; i++) {
            netTurn += angleDelta(longitudes.get(i), longitudes.get(i + 1));
        }
        
        // Determine direction
        if (Math.abs(netTurn) < 0.01) {
            return RotationDirection.NONE;
        } else if (netTurn > 0.0) {
            return RotationDirection.COUNTER_CLOCKWISE;
        } else {
            return RotationDirection.CLOCKWISE;
        }
    }
    
    /**
     * Calculates the minimum bounding rectangle for the ring.
     */
    private static Mbr calculateMbr(List<Arc> arcs, boolean containsNorthPole,
                                   boolean containsSouthPole, List<Point> points) {
        if (arcs.isEmpty()) {
            return new Mbr(-180.0, 90.0, 180.0, -90.0);
        }
        
        Mbr result = null;
        
        // Union all arc MBRs
        for (Arc arc : arcs) {
            Mbr[] arcMbrs = arc.getMbrs();
            for (Mbr arcMbr : arcMbrs) {
                if (result == null) {
                    result = arcMbr;
                } else {
                    result = unionMbrs(result, arcMbr);
                }
            }
        }
        
        // Expand to include north pole if contained but not explicitly crossed
        if (containsNorthPole && !anyPointIsNorthPole(points) && !anyArcCrossesNorthPole(arcs)) {
            result = new Mbr(-180.0, 90.0, 180.0, result.getSouth());
        }
        
        // Expand to include south pole if contained but not explicitly crossed
        if (containsSouthPole && !anyPointIsSouthPole(points) && !anyArcCrossesSouthPole(arcs)) {
            result = new Mbr(-180.0, result.getNorth(), 180.0, -90.0);
        }
        
        return result;
    }
    
    /**
     * Unions two MBRs handling geodetic wrapping.
     * Faithful translation of cmr.spatial.mbr/union.
     */
    private static Mbr unionMbrs(Mbr m1, Mbr m2) {
        double north = Math.max(m1.getNorth(), m2.getNorth());
        double south = Math.min(m1.getSouth(), m2.getSouth());
        
        boolean m1Crosses = m1.getWest() > m1.getEast();
        boolean m2Crosses = m2.getWest() > m2.getEast();
        
        double west, east;
        
        if (m1Crosses && m2Crosses) {
            // Both cross antimeridian
            west = Math.min(m1.getWest(), m2.getWest());
            east = Math.max(m1.getEast(), m2.getEast());
            
            // If result would cover whole world, set it to that
            if (west <= east) {
                west = -180.0;
                east = 180.0;
            }
        } else if (m1Crosses || m2Crosses) {
            // One crosses the antimeridian
            // Make m1 be the one that crosses
            Mbr crossing = m1Crosses ? m1 : m2;
            Mbr notCrossing = m1Crosses ? m2 : m1;
            
            double w1 = crossing.getWest();
            double e1 = crossing.getEast();
            double w2 = notCrossing.getWest();
            double e2 = notCrossing.getEast();
            
            // We could expand m1 to the east or to the west. Pick the shorter of the two.
            double westDist = w1 - w2;
            double eastDist = e2 - e1;
            
            if (westDist <= 0.0 || eastDist <= 0.0) {
                // Non-crossing is already contained
                west = w1;
                east = e1;
            } else if (eastDist < westDist) {
                // Expand east
                west = w1;
                east = e2;
            } else {
                // Expand west
                west = w2;
                east = e1;
            }
            
            // If result would cover whole world, set it to that
            if (west <= east) {
                west = -180.0;
                east = 180.0;
            }
        } else {
            // Neither crosses the antimeridian
            // Ensure m1.west <= m2.west
            if (m1.getWest() > m2.getWest()) {
                Mbr temp = m1;
                m1 = m2;
                m2 = temp;
            }
            
            double w1 = m1.getWest();
            double e1 = m1.getEast();
            double w2 = m2.getWest();
            double e2 = m2.getEast();
            
            west = Math.min(w1, w2);
            east = Math.max(e1, e2);
            
            // Check if it's shorter to cross the antimeridian
            double dist = east - west;
            double altWest = w2;
            double altEast = e1;
            double altDist = (180.0 - altWest) + (altEast - (-180.0));
            
            if (altDist < dist) {
                west = altWest;
                east = altEast;
            }
        }
        
        return new Mbr(west, north, east, south);
    }
    
    /**
     * Calculates external points (points guaranteed to be outside the ring).
     * Used for ray-casting algorithm.
     * Matches the logic in cmr.spatial.mbr/external-points.
     */
    private static List<Point> calculateExternalPoints(Mbr mbr, boolean containsNorthPole,
                                                       boolean containsSouthPole) {
        List<Point> result = new ArrayList<>();
        
        // Cannot determine external points if ring contains both poles
        if (containsNorthPole && containsSouthPole) {
            return result;
        }
        
        // Find the biggest area around the MBR to place external points
        boolean crossesAntimeridian = mbr.getWest() > mbr.getEast();
        
        double northDist = 90.0 - mbr.getNorth();
        double southDist = mbr.getSouth() - (-90.0);
        double westDist = crossesAntimeridian ? 0.0 : mbr.getWest() - (-180.0);
        double eastDist = crossesAntimeridian ? 0.0 : 180.0 - mbr.getEast();
        
        double biggestDist = Math.max(Math.max(northDist, southDist), 
                                      Math.max(westDist, eastDist));
        
        double w, n, e, s;
        
        if (biggestDist == northDist) {
            // Place points in area north of MBR
            w = -180.0;
            n = 90.0;
            e = 180.0;
            s = mbr.getNorth();
        } else if (biggestDist == southDist) {
            // Place points in area south of MBR
            w = -180.0;
            n = mbr.getSouth();
            e = 180.0;
            s = -90.0;
        } else if (!crossesAntimeridian && biggestDist == westDist) {
            // Place points in area west of MBR
            w = -180.0;
            n = 90.0;
            e = mbr.getWest();
            s = -90.0;
        } else if (!crossesAntimeridian && biggestDist == eastDist) {
            // Place points in area east of MBR
            w = mbr.getEast();
            n = 90.0;
            e = 180.0;
            s = -90.0;
        } else if (crossesAntimeridian) {
            // Place points in area between east and west (the gap when crossing antimeridian)
            w = mbr.getEast();
            n = 90.0;
            e = mbr.getWest();
            s = -90.0;
        } else {
            throw new RuntimeException("Logic error in calculateExternalPoints: " +
                "Could not determine biggest distance area");
        }
        
        // Find 3 points within the area: left, middle, right
        // These are distributed along the longitude range at the mid-latitude
        double midLon = mid(w, e);
        double rightLon = mid(w, midLon);
        double leftLon = mid(midLon, e);
        double midLat = mid(n, s);
        
        result.add(roundPointForExternalPoints(new Point(leftLon, midLat)));
        result.add(roundPointForExternalPoints(new Point(midLon, midLat)));
        result.add(roundPointForExternalPoints(new Point(rightLon, midLat)));
        
        return result;
    }
    
    /**
     * Calculates the midpoint between two values.
     */
    private static double mid(double a, double b) {
        return (a + b) / 2.0;
    }
    
    /**
     * Tests if a point is covered by (inside) this ring using ray-casting algorithm.
     * Matches the Clojure implementation in geodetic_ring.clj exactly.
     * 
     * @param point The point to test
     * @return true if the ring covers the point
     */
    public boolean coversPoint(Point point) {
        // Check if ring contains north or south pole (matches Clojure)
        if (containsNorthPole && isNorthPole(point)) {
            return true;
        }
        if (containsSouthPole && isSouthPole(point)) {
            return true;
        }
        
        // Only do real intersection if the MBR covers the point (matches Clojure)
        if (!geodeticMbrCoversPoint(mbr, point)) {
            return false;
        }
        
        // Check if point is in the point set (matches Clojure)
        for (Point p : points) {
            if (pointsEqual(p, point)) {
                return true;
            }
        }
        
        // Choose an external point for ray-casting
        Point externalPoint = chooseExternalPoint(point);
        if (externalPoint == null) {
            throw new RuntimeException("Could not find suitable external point for ring coverage test. " +
                                     "This can happen when the ring contains both poles.");
        }
        
        // Create the test arc from point to external point (matches Clojure)
        Arc crossingArc;
        try {
            crossingArc = Arc.createArc(point, externalPoint);
        } catch (IllegalArgumentException e) {
            // Points are equal or antipodal - shouldn't happen with proper external point
            return false;
        }
        
        // Find all intersections with ring arcs and round them
        Set<Point> intersections = new HashSet<>();
        for (Arc arc : arcs) {
            List<Point> arcIntersections = ArcLineSegmentIntersections.arcArcIntersections(crossingArc, arc);
            for (Point p : arcIntersections) {
                // Round to 5 decimal places to match Clojure INTERSECTION_POINT_PRECISION
                Point rounded = roundPoint(p);
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
     * Checks if an MBR covers a point in geodetic space.
     * Matches cmr.spatial.mbr/geodetic-covers-point? and geodetic-lon-range-covers-lon?
     */
    private static boolean geodeticMbrCoversPoint(Mbr mbr, Point point) {
        double lon = point.getLon();
        double lat = point.getLat();
        
        // Check latitude bounds
        if (lat < mbr.getSouth() || lat > mbr.getNorth()) {
            return false;
        }
        
        // Check longitude - matches geodetic-lon-range-covers-lon? exactly
        double west = mbr.getWest() - MBR_COVERS_TOLERANCE;
        double east = mbr.getEast() + MBR_COVERS_TOLERANCE;
        boolean crossesAntimeridian = west > east;
        
        if (crossesAntimeridian) {
            // Crosses antimeridian: longitude is in [west, 180] or [-180, east]
            return lon >= west || lon <= east;
        } else if (Math.abs(lon) == 180.0) {
            // Special case: point is on antimeridian (±180)
            // Check if west or east is within tolerance of ±180
            double within180 = 180.0 - MBR_COVERS_TOLERANCE;
            return Math.abs(west) >= within180 || Math.abs(east) >= within180;
        } else {
            // Normal case: doesn't cross antimeridian
            return lon >= west && lon <= east;
        }
    }
    
    /**
     * Checks if a point is at the North Pole.
     */
    private static boolean isNorthPole(Point p) {
        return Math.abs(p.getLat() - 90.0) < APPROXIMATION_DELTA;
    }
    
    /**
     * Checks if a point is at the South Pole.
     */
    private static boolean isSouthPole(Point p) {
        return Math.abs(p.getLat() + 90.0) < APPROXIMATION_DELTA;
    }
    
    /**
     * Chooses an external point that is not equal or antipodal to the test point.
     * Matches choose-external-point in Clojure.
     */
    private Point chooseExternalPoint(Point point) {
        // Round to EXTERNAL_POINT_PRECISION (4) to match Clojure
        Point rounded = roundPointForExternalPoints(point);
        Point antipodal = antipodalPoint(rounded);
        
        for (Point ep : externalPoints) {
            // Use exact equality since both are rounded to same precision
            if (!pointsExactlyEqual(ep, rounded) && !pointsExactlyEqual(ep, antipodal)) {
                return ep;
            }
        }
        
        return null;
    }
    
    /**
     * Checks if two points are exactly equal (no approximation).
     * Used when comparing rounded points.
     */
    private static boolean pointsExactlyEqual(Point p1, Point p2) {
        return p1.getLon() == p2.getLon() && p1.getLat() == p2.getLat();
    }
    
    /**
     * Returns the antipodal point (opposite side of the sphere).
     */
    private static Point antipodalPoint(Point p) {
        double lon = p.getLon();
        double lat = p.getLat();
        
        double antiLon = lon + 180.0;
        if (antiLon > 180.0) antiLon -= 360.0;
        
        return new Point(antiLon, -lat);
    }
    
    /**
     * Rounds a point for external point generation (precision 4).
     * Matches EXTERNAL_POINT_PRECISION in Clojure.
     */
    private static Point roundPointForExternalPoints(Point p) {
        double scale = Math.pow(10, EXTERNAL_POINT_PRECISION);
        double lon = Math.round(p.getLon() * scale) / scale;
        double lat = Math.round(p.getLat() * scale) / scale;
        return new Point(lon, lat);
    }
    
    /**
     * Rounds a point for intersection calculations (precision 5).
     * Matches INTERSECTION_POINT_PRECISION in Clojure.
     */
    private static Point roundPoint(Point p) {
        double scale = Math.pow(10, INTERSECTION_POINT_PRECISION);  // 100000.0
        double lon = Math.round(p.getLon() * scale) / scale;
        double lat = Math.round(p.getLat() * scale) / scale;
        return new Point(lon, lat);
    }
    
    /**
     * Returns true if any point in the list is at the north pole.
     */
    private static boolean anyPointIsNorthPole(List<Point> points) {
        for (Point p : points) {
            if (isNorthPole(p)) return true;
        }
        return false;
    }
    
    /**
     * Returns true if any point in the list is at the south pole.
     */
    private static boolean anyPointIsSouthPole(List<Point> points) {
        for (Point p : points) {
            if (isSouthPole(p)) return true;
        }
        return false;
    }
    
    /**
     * Returns true if any arc crosses the north pole.
     */
    private static boolean anyArcCrossesNorthPole(List<Arc> arcs) {
        for (Arc arc : arcs) {
            if (arc.crossesNorthPole()) return true;
        }
        return false;
    }
    
    /**
     * Returns true if any arc crosses the south pole.
     */
    private static boolean anyArcCrossesSouthPole(List<Arc> arcs) {
        for (Arc arc : arcs) {
            if (arc.crossesSouthPole()) return true;
        }
        return false;
    }
    
    /**
     * Returns true if two points are equal (with tolerance).
     */
    private static boolean pointsEqual(Point p1, Point p2) {
        return MathUtils.doubleApprox(p1.getLon(), p2.getLon(), APPROXIMATION_DELTA) &&
               MathUtils.doubleApprox(p1.getLat(), p2.getLat(), APPROXIMATION_DELTA);
    }
    
    // Getters
    
    public List<Point> getPointsList() {
        return points;
    }
    
    public Point[] getPoints() {
        return points.toArray(new Point[0]);
    }
    
    public List<Arc> getArcsList() {
        return arcs;
    }
    
    public Arc[] getArcs() {
        return arcs.toArray(new Arc[0]);
    }
    
    public Mbr getMbr() {
        return mbr;
    }
    
    public boolean containsNorthPole() {
        return containsNorthPole;
    }
    
    public boolean containsSouthPole() {
        return containsSouthPole;
    }
    
    /**
     * Enum for rotation direction.
     */
    public enum RotationDirection {
        CLOCKWISE,
        COUNTER_CLOCKWISE,
        NONE
    }
    
    @Override
    public String toString() {
        return String.format("GeodeticRing{points=%d, arcs=%d, northPole=%b, southPole=%b}",
                points.size(), arcs.size(), containsNorthPole, containsSouthPole);
    }
}
