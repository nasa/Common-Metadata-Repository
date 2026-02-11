package cmr.spatial.shape;

/**
 * Java representation of a Point shape.
 * Implements geodetic equality semantics matching the Clojure Point implementation.
 */
public class Point implements SpatialShape {
    private static final double APPROXIMATION_DELTA = 0.000000001;
    private static final int PRIME = 31;
    
    // Precomputed hash codes matching Clojure Point implementation
    private static final int NORTH_POLE_HASH;
    private static final int SOUTH_POLE_HASH;
    private static final int INITIAL_AM_HASH;
    
    static {
        // Matches Clojure: (int (+ (* PRIME (+ PRIME lon-hash)) (.hashCode (Double. 90.0))))
        int lonHash = Double.hashCode(0.0);
        NORTH_POLE_HASH = PRIME * (PRIME + lonHash) + Double.hashCode(90.0);
        SOUTH_POLE_HASH = PRIME * (PRIME + lonHash) + Double.hashCode(-90.0);
        // Matches Clojure: (* PRIME (+ PRIME (.hashCode (Double. 180.0))))
        INITIAL_AM_HASH = PRIME * (PRIME + Double.hashCode(180.0));
    }
    
    private final double lon;
    private final double lat;

    public Point(double lon, double lat) {
        this.lon = lon;
        this.lat = lat;
    }

    public double getLon() {
        return lon;
    }

    public double getLat() {
        return lat;
    }

    @Override
    public String getType() {
        return "point";
    }

    @Override
    public String toString() {
        return String.format("Point{lon=%f, lat=%f}", lon, lat);
    }
    
    /**
     * Checks if this point is at the north pole (latitude ~90).
     */
    private boolean isNorthPole() {
        return Math.abs(lat - 90.0) < APPROXIMATION_DELTA;
    }
    
    /**
     * Checks if this point is at the south pole (latitude ~-90).
     */
    private boolean isSouthPole() {
        return Math.abs(lat + 90.0) < APPROXIMATION_DELTA;
    }
    
    /**
     * Checks if this point is on the antimeridian (longitude ~180 or ~-180).
     */
    private boolean onAntimeridian() {
        return Math.abs(Math.abs(lon) - 180.0) < APPROXIMATION_DELTA;
    }
    
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        
        Point otherPoint = (Point) other;
        
        // Geodetic special cases
        // Any longitude value at north pole is considered equivalent
        if (isNorthPole() && otherPoint.isNorthPole()) {
            return true;
        }
        
        // Any longitude value at south pole is considered equivalent
        if (isSouthPole() && otherPoint.isSouthPole()) {
            return true;
        }
        
        // -180 and 180 are considered equivalent longitudes
        if (onAntimeridian() && otherPoint.onAntimeridian()) {
            return Math.abs(lat - otherPoint.lat) < APPROXIMATION_DELTA;
        }
        
        // Normal equality
        return Math.abs(lon - otherPoint.lon) < APPROXIMATION_DELTA &&
               Math.abs(lat - otherPoint.lat) < APPROXIMATION_DELTA;
    }
    
    @Override
    public int hashCode() {
        // Matches Clojure Point hashCode implementation for geodetic equality
        int lonHash = Double.hashCode(lon);
        int latHash = Double.hashCode(lat);
        int combinedHash = PRIME * (PRIME + lonHash) + latHash;
        
        // Geodetic special cases for consistent hashing
        if (isNorthPole()) {
            return NORTH_POLE_HASH;
        }
        if (isSouthPole()) {
            return SOUTH_POLE_HASH;
        }
        if (Math.abs(Math.abs(lon) - 180.0) < APPROXIMATION_DELTA) {
            // Points on antimeridian hash to same value regardless of sign
            return INITIAL_AM_HASH + latHash;
        }
        
        return combinedHash;
    }
}
