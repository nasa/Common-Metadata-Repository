package cmr.spatial.shape;

import java.util.*;

/**
 * Java representation of a Ring (closed sequence of points).
 * Used as a component of Polygon shapes, and as a hole in Polygon objects.
 */
public class Ring implements SpatialShape {
    private final String coordinateSystem;
    private final List<Double> ordinates;

    public Ring(String coordinateSystem, List<Double> ordinates) {
        this.coordinateSystem = coordinateSystem;
        this.ordinates = new ArrayList<>(ordinates);
    }

    public String getCoordinateSystem() {
        return coordinateSystem;
    }

    public List<Double> getOrdinates() {
        return ordinates;
    }

    @Override
    public String getType() {
        return coordinateSystem + "-hole";
    }

    @Override
    public String toString() {
        return String.format("Ring{cs=%s, ords=%d}", coordinateSystem, ordinates.size());
    }
}
