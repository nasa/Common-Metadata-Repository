package cmr.spatial.internal.arc;

import cmr.spatial.shape.Mbr;
import cmr.spatial.shape.Point;
import cmr.spatial.math.CoordinateConversion;
import cmr.spatial.math.MathUtils;
import cmr.spatial.math.Vector;
import cmr.spatial.geometry.MbrIntersections;

/**
 * Represents an arc on a sphere between two points along a great circle.
 * An arc is the shortest path between two points on a sphere, following a great circle.
 * Immutable value class with cached derived information (MBRs, great circle, courses).
 * 
 * The arc is ordered from west to east, and contains precomputed bounding rectangles
 * to optimize spatial queries.
 */
public final class Arc {
    
    /**
     * The delta in degrees to use when comparing things approximately.
     */
    public static final double APPROXIMATION_DELTA = 0.0000001;
    
    private final Point westPoint;
    private final Point eastPoint;
    private final GreatCircle greatCircle;
    private final double initialCourse;
    private final double endingCourse;
    private final Mbr mbr1;
    private final Mbr mbr2; // May be null if arc only has one MBR
    
    /**
     * Private constructor. Use createArc() factory method instead.
     */
    private Arc(Point westPoint, Point eastPoint, GreatCircle greatCircle,
                double initialCourse, double endingCourse, Mbr mbr1, Mbr mbr2) {
        this.westPoint = westPoint;
        this.eastPoint = eastPoint;
        this.greatCircle = greatCircle;
        this.initialCourse = initialCourse;
        this.endingCourse = endingCourse;
        this.mbr1 = mbr1;
        this.mbr2 = mbr2;
    }
    
    /**
     * Creates an arc from two points, ordering them west-to-east.
     * 
     * @param p1 First point
     * @param p2 Second point
     * @return Arc from p1 to p2
     * @throws IllegalArgumentException if points are equal or antipodal
     */
    public static Arc createArc(Point p1, Point p2) {
        if (p1.equals(p2)) {
            throw new IllegalArgumentException("Cannot create arc from equal points");
        }
        if (areAntipodal(p1, p2)) {
            throw new IllegalArgumentException("Cannot create arc from antipodal points");
        }
        
        // Order points west to east
        if (comparePoints(p1, p2) < 0) {
            return createArcFromOrderedPoints(p1, p2, p1, p2);
        } else {
            return createArcFromOrderedPoints(p1, p2, p2, p1);
        }
    }
    
    /**
     * Creates an arc from points that are already ordered west to east.
     * 
     * @param point1 First point (original order)
     * @param point2 Second point (original order)
     * @param westPoint Western-most point
     * @param eastPoint Eastern-most point
     * @return Newly created arc
     */
    private static Arc createArcFromOrderedPoints(Point point1, Point point2,
                                                   Point westPoint, Point eastPoint) {
        GreatCircle gc = calculateGreatCircle(westPoint, eastPoint);
        double initialCourse = calculateCourse(point1, point2);
        double endingCourse = (calculateCourse(point2, point1) + 180.0) % 360.0;
        
        Mbr[] mbrs = calculateBoundingRectangles(westPoint, eastPoint, gc, 
                                                  initialCourse, endingCourse);
        
        return new Arc(westPoint, eastPoint, gc, initialCourse, endingCourse,
                      mbrs[0], mbrs.length > 1 ? mbrs[1] : null);
    }
    
    /**
     * Calculates the great circle that contains this arc.
     */
    private static GreatCircle calculateGreatCircle(Point westPoint, Point eastPoint) {
        // Calculate plane vector using cross product
        Vector planeVector = CoordinateConversion.lonLatCrossProduct(westPoint, eastPoint)
                                                  .normalize();
        Point pvPoint = CoordinateConversion.vectorToPoint(planeVector);
        
        double plon = pvPoint.getLon();
        double plat = pvPoint.getLat();
        
        // Calculate northernmost and southernmost points on the great circle
        double northLon = plon;
        double northLat = 90.0 - Math.abs(plat);
        
        double southLon = (plon + 180.0 > 180.0) ? plon - 180.0 : plon + 180.0;
        double southLat = Math.abs(plat) - 90.0;
        
        Point northernmostPoint = new Point(northLon, northLat);
        Point southernmostPoint = new Point(southLon, southLat);
        
        return new GreatCircle(planeVector, northernmostPoint, southernmostPoint);
    }
    
    /**
     * Calculates bounding rectangles for the arc.
     * May return 1 or 2 MBRs depending on whether the arc crosses a pole.
     */
    private static Mbr[] calculateBoundingRectangles(Point westPoint, Point eastPoint,
                                                     GreatCircle greatCircle,
                                                     double initialCourse, 
                                                     double endingCourse) {
        double wLon = westPoint.getLon();
        double wLat = westPoint.getLat();
        double eLon = eastPoint.getLon();
        double eLat = eastPoint.getLat();
        
        // Check if crosses north pole
        if (initialCourse == 0.0 && endingCourse == 180.0) {
            Mbr br1 = new Mbr(wLon, 90.0, wLon, wLat);
            Mbr br2 = new Mbr(eLon, 90.0, eLon, eLat);
            return new Mbr[] { br1, br2 };
        }
        
        // Check if crosses south pole
        if (initialCourse == 180.0 && endingCourse == 0.0) {
            Mbr br1 = new Mbr(wLon, wLat, wLon, -90.0);
            Mbr br2 = new Mbr(eLon, eLat, eLon, -90.0);
            return new Mbr[] { br1, br2 };
        }
        
        // Regular arc (not crossing poles)
        double w = wLon;
        double e = eLon;
        
        // If one point is at a pole, west and east longitudes should match
        if (isNorthPole(westPoint) || isSouthPole(westPoint)) {
            w = e;
        }
        if (isNorthPole(eastPoint) || isSouthPole(eastPoint)) {
            e = w;
        }
        
        // Choose north and south extents
        double s = Math.min(wLat, eLat);
        double n = Math.max(wLat, eLat);
        
        // If both on antimeridian, set west and east to same value
        boolean bothAntimeridian = (Math.abs(w) == 180.0) && (Math.abs(e) == 180.0);
        if (bothAntimeridian) {
            w = 180.0;
            e = 180.0;
        }
        
        Mbr br = new Mbr(w, n, e, s);
        Point northernmost = greatCircle.getNorthernmostPoint();
        Point southernmost = greatCircle.getSouthernmostPoint();
        
        // Check if great circle extrema are within the arc's longitude range
        if (coversLonGeodetic(br, northernmost.getLon())) {
            // Expand north to include northernmost point
            return new Mbr[] { new Mbr(br.getWest(), northernmost.getLat(), 
                                       br.getEast(), br.getSouth()) };
        } else if (coversLonGeodetic(br, southernmost.getLon())) {
            // Expand south to include southernmost point
            return new Mbr[] { new Mbr(br.getWest(), br.getNorth(), 
                                       br.getEast(), southernmost.getLat()) };
        } else {
            return new Mbr[] { br };
        }
    }
    
    /**
     * Returns true if the MBR covers the given longitude (geodetic).
     */
    private static boolean coversLonGeodetic(Mbr mbr, double lon) {
        double west = mbr.getWest();
        double east = mbr.getEast();
        double tolerance = MathUtils.COVERS_TOLERANCE;
        
        west = west - tolerance;
        east = east + tolerance;
        boolean crossesAntimeridian = west > east;
        
        if (crossesAntimeridian) {
            return lon >= west || lon <= east;
        } else if (Math.abs(lon) == 180.0) {
            double within180 = 180.0 - tolerance;
            return Math.abs(west) >= within180 || Math.abs(east) >= within180;
        } else {
            return lon >= west && lon <= east;
        }
    }
    
    /**
     * Compares two points from west to east. If longitudes are equal, compares south to north.
     * Returns negative if p1 is west/south of p2, 0 if equal, positive if p1 is east/north of p2.
     */
    private static int comparePoints(Point p1, Point p2) {
        double lon1 = p1.getLon();
        double lat1 = p1.getLat();
        double lon2 = p2.getLon();
        double lat2 = p2.getLat();
        
        if (lon1 == lon2) {
            return Double.compare(lat1, lat2);
        }
        
        // Compare longitudes west to east
        return compareLongitudes(lon1, lon2);
    }
    
    /**
     * Compares longitudes from west to east.
     * Returns -1 if l1 is west of l2, 0 if equal, 1 if l1 is east of l2.
     */
    private static int compareLongitudes(double l1, double l2) {
        double mod = (l1 - l2) % 360.0;
        if (mod < 0) mod += 360.0;
        
        if (mod == 180.0) {
            return Double.compare(l1, l2);
        } else if (mod == 0.0) {
            return (l1 == 180.0) ? -1 : 1;
        } else if (mod < 180.0) {
            return 1;  // l1 is east of l2
        } else {
            return -1;  // l1 is west of l2
        }
    }
    
    /**
     * Returns true if two points are antipodal (opposite sides of the sphere).
     */
    private static boolean areAntipodal(Point p1, Point p2) {
        double lat1 = p1.getLat();
        double lat2 = p2.getLat();
        double lon1 = p1.getLon();
        double lon2 = p2.getLon();
        
        if (isNorthPole(p1)) return isSouthPole(p2);
        if (isSouthPole(p1)) return isNorthPole(p2);
        
        return (lat1 == -lat2) && (lon1 == antipodalLon(lon2));
    }
    
    /**
     * Returns the longitude on the opposite side of the earth.
     */
    private static double antipodalLon(double lon) {
        double newLon = lon + 180.0;
        return (newLon > 180.0) ? newLon - 360.0 : newLon;
    }
    
    /**
     * Calculates the initial bearing/course from p1 to p2 in degrees.
     * 0 = north, 90 = east, 180 = south, 270 = west.
     */
    private static double calculateCourse(Point p1, Point p2) {
        double lon1 = p1.getLon();
        double lat1 = p1.getLat();
        double lon2 = p2.getLon();
        double lat2 = p2.getLat();
        
        // Handle poles
        if (isNorthPole(p2)) return 0.0;
        if (isNorthPole(p1)) return 180.0;
        if (isSouthPole(p2)) return 180.0;
        if (isSouthPole(p1)) return 0.0;
        
        // Handle vertical lines
        if (lon1 == lon2) {
            return (lat1 > lat2) ? 180.0 : 0.0;
        }
        
        // Check if crossing a pole (180 degrees longitude difference)
        if (Math.abs(lon1 - lon2) == 180.0) {
            double midLat = (lat1 + lat2) / 2.0;
            return (midLat > 0.0) ? 0.0 : 180.0;
        }
        
        // General case: calculate bearing
        double lon1Rad = Math.toRadians(lon1);
        double lat1Rad = Math.toRadians(lat1);
        double lon2Rad = Math.toRadians(lon2);
        double lat2Rad = Math.toRadians(lat2);
        
        double lonDiff = lon2Rad - lon1Rad;
        double cosLat2 = Math.cos(lat2Rad);
        double y = Math.sin(lonDiff) * cosLat2;
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) - 
                   Math.sin(lat1Rad) * cosLat2 * Math.cos(lonDiff);
        
        double normalized = Math.toDegrees(Math.atan2(y, x));
        return (-normalized + 360.0) % 360.0;
    }
    
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
     * Returns true if point is at either pole.
     */
    private static boolean isPole(Point p) {
        return isNorthPole(p) || isSouthPole(p);
    }
    
    // Getters
    
    public Point getWestPoint() {
        return westPoint;
    }
    
    public Point getEastPoint() {
        return eastPoint;
    }
    
    public GreatCircle getGreatCircle() {
        return greatCircle;
    }
    
    public double getInitialCourse() {
        return initialCourse;
    }
    
    public double getEndingCourse() {
        return endingCourse;
    }
    
    /**
     * Returns the minimum bounding rectangles for this arc.
     * May return 1 or 2 MBRs depending on arc geometry.
     */
    public Mbr[] getMbrs() {
        if (mbr2 == null) {
            return new Mbr[] { mbr1 };
        } else {
            return new Mbr[] { mbr1, mbr2 };
        }
    }

    /**
     * Returns the first minimum bounding rectangle.
     */
    public Mbr getMbr1() {
        return mbr1;
    }

    /**
     * Returns the second minimum bounding rectangle, or null if arc has only one MBR.
     */
    public Mbr getMbr2() {
        return mbr2;
    }
    
    /**
     * Returns true if the arc crosses the north pole.
     */
    public boolean crossesNorthPole() {
        return initialCourse == 0.0 && endingCourse == 180.0;
    }
    
    /**
     * Returns true if the arc crosses the south pole.
     */
    public boolean crossesSouthPole() {
        return initialCourse == 180.0 && endingCourse == 0.0;
    }
    
    /**
     * Returns true if the arc crosses the antimeridian (±180° longitude).
     */
    public boolean crossesAntimeridian() {
        return crossesAntimeridianMbr(mbr1) || 
               (mbr2 != null && crossesAntimeridianMbr(mbr2));
    }
    
    /**
     * Returns true if an MBR crosses the antimeridian.
     */
    private static boolean crossesAntimeridianMbr(Mbr mbr) {
        return mbr.getWest() > mbr.getEast();
    }
    
    /**
     * Returns true if the arc is vertical (north-south).
     * Crossing a pole is considered vertical.
     */
    public boolean isVertical() {
        double wLon = westPoint.getLon();
        double eLon = eastPoint.getLon();
        
        // Longitudes are equal
        if (MathUtils.doubleApprox(wLon, eLon, APPROXIMATION_DELTA)) {
            return true;
        }
        
        // Crosses a pole
        if (crossesNorthPole() || crossesSouthPole()) {
            return true;
        }
        
        // One point is a pole (but not both)
        boolean westIsPole = isPole(westPoint);
        boolean eastIsPole = isPole(eastPoint);
        if (westIsPole != eastIsPole) {
            return true;
        }
        
        // Both on antimeridian
        if (MathUtils.doubleApprox(180.0, Math.abs(wLon), APPROXIMATION_DELTA) &&
            MathUtils.doubleApprox(180.0, Math.abs(eLon), APPROXIMATION_DELTA)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Returns true if the given point lies on this arc.
     * 
     * @param point Point to test
     * @return true if point is on the arc
     */
    public boolean pointOnArc(Point point) {
        // Check if point is in one of the MBRs
        if (!coversPointGeodetic(mbr1, point) && 
            (mbr2 == null || !coversPointGeodetic(mbr2, point))) {
            return false;
        }
        
        // Check if point is one of the endpoints
        if (pointsApproxEqual(westPoint, point, APPROXIMATION_DELTA) ||
            pointsApproxEqual(eastPoint, point, APPROXIMATION_DELTA)) {
            return true;
        }
        
        // If arc is vertical and point is in MBR, it's on the arc
        if (isVertical()) {
            return true;
        }
        
        // Find point on arc at the given longitude and check if it matches
        Point pointAtLon = pointAtLon(point.getLon());
        return pointAtLon != null && 
               pointsApproxEqual(point, pointAtLon, APPROXIMATION_DELTA);
    }
    
    /**
     * Returns the point on the arc at the given longitude.
     * Returns null if the longitude is outside the arc's bounds.
     * Does not work for vertical arcs.
     * 
     * @param lon Longitude in degrees
     * @return Point on arc at given longitude, or null if outside bounds
     */
    public Point pointAtLon(double lon) {
        if (isVertical()) {
            throw new IllegalStateException("Cannot call pointAtLon on vertical arc");
        }
        
        // Check if longitude is within arc bounds
        boolean inBounds = coversLonGeodetic(mbr1, lon) ||
                          (mbr2 != null && coversLonGeodetic(mbr2, lon)) ||
                          crossesSouthPole() || crossesNorthPole();
        
        if (!inBounds) {
            return null;
        }
        
        double lonRad = Math.toRadians(lon);
        double lon1 = Math.toRadians(westPoint.getLon());
        double lon2 = Math.toRadians(eastPoint.getLon());
        double lat1 = Math.toRadians(westPoint.getLat());
        double lat2 = Math.toRadians(eastPoint.getLat());
        
        double cosLat1 = Math.cos(lat1);
        double cosLat2 = Math.cos(lat2);
        
        // From http://williams.best.vwh.net/avform.htm#Int
        double top = Math.sin(lat1) * cosLat2 * Math.sin(lonRad - lon2) -
                     Math.sin(lat2) * cosLat1 * Math.sin(lonRad - lon1);
        double bottom = cosLat1 * cosLat2 * Math.sin(lon1 - lon2);
        double latRad = Math.atan(top / bottom);
        
        return new Point(Math.toDegrees(lonRad), Math.toDegrees(latRad));
    }
    
    /**
     * Returns the points where the arc crosses the given latitude.
     * Returns null if the arc doesn't cross that latitude.
     * 
     * @param lat Latitude in degrees
     * @return Array of points (0, 1, or 2 points), or null if no intersections
     */
    public Point[] pointsAtLat(double lat) {
        // Check if latitude is within arc bounds
        if (!coversLatMbr(mbr1, lat) && (mbr2 == null || !coversLatMbr(mbr2, lat))) {
            return null;
        }
        
        double lat3 = Math.toRadians(lat);
        double lon1 = Math.toRadians(westPoint.getLon());
        double lon2 = Math.toRadians(eastPoint.getLon());
        double lat1 = Math.toRadians(westPoint.getLat());
        double lat2 = Math.toRadians(eastPoint.getLat());
        
        double lon12 = lon1 - lon2;
        double sinLon12 = Math.sin(lon12);
        double cosLon12 = Math.cos(lon12);
        double sinLat1 = Math.sin(lat1);
        double cosLat1 = Math.cos(lat1);
        double sinLat2 = Math.sin(lat2);
        double cosLat2 = Math.cos(lat2);
        double sinLat3 = Math.sin(lat3);
        double cosLat3 = Math.cos(lat3);
        
        // From http://williams.best.vwh.net/avform.htm#Par
        double A = sinLat1 * cosLat2 * cosLat3 * sinLon12;
        double B = sinLat1 * cosLat2 * cosLat3 * cosLon12 - cosLat1 * sinLat2 * cosLat3;
        double C = cosLat1 * cosLat2 * sinLat3 * sinLon12;
        double h = Math.sqrt(A * A + B * B);
        
        if (Math.abs(C) > h) {
            return null;  // No intersection
        }
        
        double lonRad = Math.atan2(B, A);
        double dlon = Math.acos(C / h);
        
        double lon31 = ((lon1 + dlon + lonRad + Math.PI) % (2 * Math.PI)) - Math.PI;
        double lon32 = ((lon1 - dlon + lonRad + Math.PI) % (2 * Math.PI)) - Math.PI;
        
        Point p1 = new Point(Math.toDegrees(lon31), lat);
        Point p2 = new Point(Math.toDegrees(lon32), lat);
        
        // Check if points are within MBRs
        boolean p1InMbr = coversPointGeodetic(mbr1, p1) || 
                         (mbr2 != null && coversPointGeodetic(mbr2, p1));
        boolean p2InMbr = coversPointGeodetic(mbr1, p2) || 
                         (mbr2 != null && coversPointGeodetic(mbr2, p2));
        
        if (p1InMbr && p2InMbr) {
            if (pointsApproxEqual(p1, p2, APPROXIMATION_DELTA)) {
                return new Point[] { p1 };
            } else {
                return new Point[] { p1, p2 };
            }
        } else if (p1InMbr) {
            return new Point[] { p1 };
        } else if (p2InMbr) {
            return new Point[] { p2 };
        } else {
            return null;
        }
    }
    
    /**
     * Returns true if MBR covers the given latitude.
     */
    private static boolean coversLatMbr(Mbr mbr, double lat) {
        double tolerance = MathUtils.COVERS_TOLERANCE;
        double north = mbr.getNorth() + tolerance;
        double south = mbr.getSouth() - tolerance;
        return lat >= south && lat <= north;
    }
    
    /**
     * Returns true if MBR covers the given point (geodetic).
     */
    private static boolean coversPointGeodetic(Mbr mbr, Point p) {
        double tolerance = MathUtils.COVERS_TOLERANCE;
        double lat = p.getLat();
        
        // Handle poles
        if (isNorthPole(p)) {
            return coversLatMbr(mbr, 90.0);
        }
        if (isSouthPole(p)) {
            return coversLatMbr(mbr, -90.0);
        }
        
        return coversLatMbr(mbr, lat) && coversLonGeodetic(mbr, p.getLon());
    }
    
    /**
     * Returns true if two points are approximately equal within the given delta.
     */
    private static boolean pointsApproxEqual(Point p1, Point p2, double delta) {
        double lat1 = p1.getLat();
        double lat2 = p2.getLat();
        double lon1 = p1.getLon();
        double lon2 = p2.getLon();
        
        if (!MathUtils.doubleApprox(lat1, lat2, delta)) {
            return false;
        }
        
        if (MathUtils.doubleApprox(lon1, lon2, delta)) {
            return true;
        }
        
        // Check if both on antimeridian
        if (Math.abs(lon1) == 180.0 && Math.abs(lon2) == 180.0) {
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
    
    @Override
    public String toString() {
        return String.format("Arc{west=%s, east=%s, courses=[%.1f, %.1f]}", 
                           westPoint, eastPoint, initialCourse, endingCourse);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Arc)) return false;
        Arc other = (Arc) obj;
        return westPoint.equals(other.westPoint) 
            && eastPoint.equals(other.eastPoint)
            && Double.compare(initialCourse, other.initialCourse) == 0
            && Double.compare(endingCourse, other.endingCourse) == 0;
    }
    
    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + westPoint.hashCode();
        result = 31 * result + eastPoint.hashCode();
        long temp = Double.doubleToLongBits(initialCourse);
        result = 31 * result + (int)(temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(endingCourse);
        result = 31 * result + (int)(temp ^ (temp >>> 32));
        return result;
    }
}
