package cmr.spatial.shape;

import java.util.*;

/**
 * Java representation of a Ring (closed sequence of points).
 * Used as a component of Polygon shapes, and as a hole in Polygon objects.
 */
public class Ring implements SpatialShape {
    private final String coordinateSystem;
    private final List<Double> ordinates;
    private final boolean isHole;

    public Ring(String coordinateSystem, List<Double> ordinates) {
        this(coordinateSystem, ordinates, false);
    }

    public Ring(String coordinateSystem, List<Double> ordinates, boolean isHole) {
        this.coordinateSystem = coordinateSystem;
        this.ordinates = new ArrayList<>(ordinates);
        this.isHole = isHole;
    }

    public String getCoordinateSystem() {
        return coordinateSystem;
    }

    public List<Double> getOrdinates() {
        return Collections.unmodifiableList(ordinates);
    }

    public boolean isHole() {
        return isHole;
    }

    @Override
    public String getType() {
        return coordinateSystem + (isHole ? "-hole" : "-ring");
    }

    @Override
    public String toString() {
        return String.format("Ring{cs=%s, ords=%d}", coordinateSystem, ordinates.size());
    }
}
