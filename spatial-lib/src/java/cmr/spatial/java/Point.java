package cmr.spatial.java;

/**
 * Java representation of a Point shape.
 */
public class Point implements SpatialShape {
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
}
