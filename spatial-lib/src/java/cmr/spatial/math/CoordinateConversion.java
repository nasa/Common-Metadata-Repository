package cmr.spatial.math;

import cmr.spatial.shape.Point;

/**
 * Coordinate conversion utilities for spherical geometry.
 * Converts between lon/lat points and 3D vectors on unit sphere.
 */
public class CoordinateConversion {

    /**
     * A safer version of the cross product for lon/lat points.
     * Based on formula from http://williams.best.vwh.net/intersect.htm
     * 
     * Original formulas (simplified to avoid duplicate calculations):
     * x = sin(lat1-lat2) * sin((lon1+lon2)/2) * cos((lon1-lon2)/2) 
     *     - sin(lat1+lat2) * cos((lon1+lon2)/2) * sin((lon1-lon2)/2)
     * y = sin(lat1-lat2) * cos((lon1+lon2)/2) * cos((lon1-lon2)/2) 
     *     + sin(lat1+lat2) * sin((lon1+lon2)/2) * sin((lon1-lon2)/2)
     * z = cos(lat1) * cos(lat2) * sin(lon1-lon2)
     */
    public static Vector lonLatCrossProduct(Point p1, Point p2) {
        double lon1Rad = Math.toRadians(p1.getLon());
        double lat1Rad = Math.toRadians(p1.getLat());
        double lon2Rad = Math.toRadians(p2.getLon());
        double lat2Rad = Math.toRadians(p2.getLat());

        double b = (lon1Rad + lon2Rad) / 2.0;
        double sinB = Math.sin(b);
        double cosB = Math.cos(b);

        double c = (lon1Rad - lon2Rad) / 2.0;
        double e = Math.sin(lat1Rad - lat2Rad) * Math.cos(c);
        double f = Math.sin(lat1Rad + lat2Rad) * Math.sin(c);

        double x = e * sinB - f * cosB;
        double y = e * cosB + f * sinB;
        double z = Math.cos(lat1Rad) * Math.cos(lat2Rad) * Math.sin(lon1Rad - lon2Rad);

        return new Vector(x, y, z);
    }

    /**
     * Converts a point (lon/lat) to a 3D vector on the unit sphere.
     */
    public static Vector pointToVector(Point p) {
        double lonRad = Math.toRadians(p.getLon());
        double latRad = Math.toRadians(p.getLat());
        
        double cosLat = Math.cos(latRad);
        double x = cosLat * Math.cos(lonRad);
        double y = -1.0 * cosLat * Math.sin(lonRad);
        double z = Math.sin(latRad);
        
        return new Vector(x, y, z);
    }

    /**
     * Converts a 3D vector to a point (lon/lat in degrees).
     */
    public static Point vectorToPoint(Vector v) {
        double x = v.getX();
        double y = v.getY();
        double z = v.getZ();
        
        double lonRad = Math.atan2(-1.0 * y, x);
        double latRad = Math.atan2(z, Math.sqrt(x * x + y * y));
        
        return new Point(Math.toDegrees(lonRad), Math.toDegrees(latRad));
    }
}
