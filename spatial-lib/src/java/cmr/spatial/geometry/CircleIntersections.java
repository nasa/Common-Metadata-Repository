package cmr.spatial.geometry;

import cmr.spatial.shape.Circle;
import cmr.spatial.shape.LineString;
import cmr.spatial.shape.Mbr;
import cmr.spatial.shape.Point;
import cmr.spatial.shape.Polygon;
import cmr.spatial.shape.Ring;
import cmr.spatial.internal.ring.GeodeticRing;
import cmr.spatial.math.MathUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implements Circle intersection testing.
 * Translates covers-point? and circle->polygon from circle.clj.
 * 
 * A circle is defined by a center point and radius (in meters).
 * For complex intersections, circles are converted to polygon approximations.
 */
public class CircleIntersections {

    /**
     * Minimum radius in meters.
     */
    public static final double MIN_RADIUS = 10.0;
    
    /**
     * Maximum radius in meters.
     */
    public static final double MAX_RADIUS = 6000000.0;
    
    /**
     * Radius of the earth in meters for polygon approximation.
     * Matches EARTH_RADIUS_APPROX in circle.clj.
     */
    private static final double EARTH_RADIUS_APPROX = 6378137.0;
    
    /**
     * Default number of points for polygon approximation.
     * More points = more accurate but slower.
     */
    private static final int DEFAULT_NUM_POINTS = 64;

    /**
     * Validates circle parameters.
     * 
     * @param circle The circle to validate
     * @param numPoints Number of points (if applicable)
     * @throws IllegalArgumentException if parameters are invalid
     */
    private static void validateCircleParams(Circle circle, int numPoints) {
        double radius = circle.getRadius();
        
        if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
            throw new IllegalArgumentException(
                String.format("Circle radius must be between %f and %f meters, got: %f",
                    MIN_RADIUS, MAX_RADIUS, radius));
        }
        
        if (numPoints < 3) {
            throw new IllegalArgumentException(
                String.format("numPoints must be >= 3 for a valid polygon, got: %d", numPoints));
        }
    }

    /**
     * Tests if a circle covers a point.
     * A circle covers a point if the distance from the center to the point
     * is less than or equal to the radius.
     *
     * @param circle The circle to test
     * @param point The point to check
     * @return true if the circle covers the point
     */
    public static boolean coversPoint(Circle circle, Point point) {
        validateCircleParams(circle, 3); // numPoints not used here, but validate radius
        double distance = MathUtils.distance(circle.getCenter(), point);
        return distance <= circle.getRadius();
    }

    /**
     * Converts a circle to a polygon approximation with the default number of points.
     * 
     * @param circle The circle to convert
     * @return A geodetic polygon approximating the circle
     */
    public static Polygon circleToPolygon(Circle circle) {
        return circleToPolygon(circle, DEFAULT_NUM_POINTS);
    }

    /**
     * Converts a circle to a polygon approximation.
     * 
     * Algorithm from circle.clj:circle->polygon, which is based on:
     * https://github.com/gabzim/circle-to-polygon
     * 
     * Creates a polygon by calculating n points around the circle's perimeter
     * using spherical geometry. Points are calculated by moving along great circles
     * at the specified radius from the center.
     * 
     * @param circle The circle to convert
     * @param numPoints Number of points in the polygon (more = more accurate)
     * @return A geodetic polygon approximating the circle
     */
    public static Polygon circleToPolygon(Circle circle, int numPoints) {
        validateCircleParams(circle, numPoints);
        
        Point center = circle.getCenter();
        double radius = circle.getRadius();
        
        double lon1Rad = Math.toRadians(center.getLon());
        double lat1Rad = Math.toRadians(center.getLat());
        double rRad = radius / EARTH_RADIUS_APPROX;
        
        List<Point> points = new ArrayList<>();
        
        for (int i = 0; i <= numPoints; i++) {
            double theta = -2.0 * Math.PI * (i / (double) numPoints);
            
            // Calculate point at this angle around the circle
            double pLatRad = Math.asin(
                Math.sin(lat1Rad) * Math.cos(rRad) +
                Math.cos(lat1Rad) * Math.sin(rRad) * Math.cos(theta)
            );
            
            double pLonDelta = Math.atan2(
                Math.sin(theta) * Math.sin(rRad) * Math.cos(lat1Rad),
                Math.cos(rRad) - Math.sin(lat1Rad) * Math.sin(pLatRad)
            );
            
            double pLon = sanitizeLongitude(Math.toDegrees(lon1Rad + pLonDelta));
            double pLat = Math.toDegrees(pLatRad);
            
            points.add(new Point(pLon, pLat));
        }
        
        // Replace last point with first to ensure closed ring
        // (handles precision issues)
        points.set(points.size() - 1, points.get(0));
        
        // Convert points to ordinates
        List<Double> ordinates = new ArrayList<>();
        for (Point p : points) {
            ordinates.add(p.getLon());
            ordinates.add(p.getLat());
        }
        
        // Create a Ring and wrap in Polygon
        Ring ring = new Ring("geodetic", ordinates);
        return new Polygon("geodetic", Collections.singletonList(ring));
    }

    /**
     * Tests if a circle intersects a polygon.
     * Converts the circle to a polygon approximation and delegates to polygon intersection.
     * 
     * @param circle The circle to test
     * @param polygon The polygon to check
     * @return true if the circle intersects the polygon
     */
    public static boolean intersectsPolygon(Circle circle, Polygon polygon) {
        Polygon circlePoly = circleToPolygon(circle);
        return PolygonIntersections.intersectsPolygon(circlePoly, polygon);
    }

    /**
     * Tests if a circle intersects a ring.
     * Converts the circle to a polygon approximation and delegates to polygon-ring intersection.
     * 
     * @param circle The circle to test
     * @param ring The ring to check
     * @return true if the circle intersects the ring
     */
    public static boolean intersectsRing(Circle circle, Ring ring) {
        Polygon circlePoly = circleToPolygon(circle);
        return PolygonIntersections.intersectsRing(circlePoly, ring);
    }

    /**
     * Tests if a circle intersects a bounding rectangle.
     * Converts the circle to a polygon approximation and delegates to polygon-BR intersection.
     * 
     * @param circle The circle to test
     * @param mbr The bounding rectangle to check
     * @return true if the circle intersects the bounding rectangle
     */
    public static boolean intersectsBr(Circle circle, Mbr mbr) {
        Polygon circlePoly = circleToPolygon(circle);
        return PolygonIntersections.intersectsBr(circlePoly, mbr);
    }

    /**
     * Tests if a circle intersects a line string.
     * Converts the circle to a polygon approximation and delegates to polygon-linestring intersection.
     * 
     * @param circle The circle to test
     * @param lineString The line string to check
     * @return true if the circle intersects the line string
     */
    public static boolean intersectsLineString(Circle circle, LineString lineString) {
        Polygon circlePoly = circleToPolygon(circle);
        return PolygonIntersections.intersectsLineString(circlePoly, lineString);
    }

    /**
     * Sanitizes longitude to be within -180 to 180 range.
     * 
     * @param lon Longitude in degrees
     * @return Sanitized longitude
     */
    private static double sanitizeLongitude(double lon) {
        if (lon > 180.0) {
            return lon - 360.0;
        } else if (lon < -180.0) {
            return lon + 360.0;
        }
        return lon;
    }
}
