package cmr.spatial.shape;

import java.util.*;

/**
 * Java representation of a LineString shape.
 */
public class LineString implements SpatialShape {
    private final String coordinateSystem;
    private final List<Double> ordinates;

    public LineString(String coordinateSystem, List<Double> ordinates) {
        this.coordinateSystem = coordinateSystem;
        this.ordinates = new ArrayList<>(ordinates);
    }

    public String getCoordinateSystem() {
        return coordinateSystem;
    }

    public List<Double> getOrdinates() {
        return Collections.unmodifiableList(ordinates);
    }

    @Override
    public String getType() {
        return coordinateSystem + "-line-string";
    }

    @Override
    public String toString() {
        return String.format("LineString{cs=%s, ords=%d}", coordinateSystem, ordinates.size());
    }
}
