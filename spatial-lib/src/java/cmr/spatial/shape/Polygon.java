package cmr.spatial.shape;

import java.util.*;

/**
 * Java representation of a Polygon shape.
 * Consists of an outer ring (boundary) and optional inner rings (holes).
 */
public class Polygon implements SpatialShape {
    private final String coordinateSystem;
    private final List<Ring> rings;

    public Polygon(String coordinateSystem, List<Ring> rings) {
        this.coordinateSystem = coordinateSystem;
        this.rings = new ArrayList<>(rings);
    }

    public String getCoordinateSystem() {
        return coordinateSystem;
    }

    public List<Ring> getRings() {
        return Collections.unmodifiableList(rings);
    }

    /**
     * Internal method for shape deserialization only.
     * Provides mutable access to rings list for OrdsInfoShapes to add holes during construction.
     * Do not use in application code - use getRings() instead.
     */
    public List<Ring> getMutableRings() {
        return rings;
    }

    public Ring getBoundary() {
        return rings.isEmpty() ? null : rings.get(0);
    }

    public List<Ring> getHoles() {
        return rings.size() <= 1 ? Collections.emptyList() : rings.subList(1, rings.size());
    }

    @Override
    public String getType() {
        return coordinateSystem + "-polygon";
    }

    @Override
    public String toString() {
        return String.format("Polygon{cs=%s, rings=%d}", coordinateSystem, rings.size());
    }
}
