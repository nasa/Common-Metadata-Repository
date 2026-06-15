package cmr.spatial.shape;

/**
 * Java representation of a Minimum Bounding Rectangle (MBR).
 * Stores west, north, east, south coordinates.
 */
public class Mbr implements SpatialShape {
    private final double west;
    private final double north;
    private final double east;
    private final double south;

    public Mbr(double west, double north, double east, double south) {
        this.west = west;
        this.north = north;
        this.east = east;
        this.south = south;
    }

    public double getWest() {
        return west;
    }

    public double getNorth() {
        return north;
    }

    public double getEast() {
        return east;
    }

    public double getSouth() {
        return south;
    }

    @Override
    public String getType() {
        return "br";
    }

    @Override
    public String toString() {
        return String.format("Mbr{w=%f, n=%f, e=%f, s=%f}", west, north, east, south);
    }
}
