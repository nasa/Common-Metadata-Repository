package cmr.spatial.internal.arc;

import cmr.spatial.shape.Point;
import cmr.spatial.math.Vector;

/**
 * Represents a great circle on a sphere.
 * A great circle is the intersection of the sphere with a plane passing through the sphere's center.
 * Immutable value class.
 */
public final class GreatCircle {
    private final Vector planeVector;
    private final Point northernmostPoint;
    private final Point southernmostPoint;

    public GreatCircle(Vector planeVector, Point northernmostPoint, Point southernmostPoint) {
        this.planeVector = planeVector;
        this.northernmostPoint = northernmostPoint;
        this.southernmostPoint = southernmostPoint;
    }

    public Vector getPlaneVector() {
        return planeVector;
    }

    public Point getNorthernmostPoint() {
        return northernmostPoint;
    }

    public Point getSouthernmostPoint() {
        return southernmostPoint;
    }

    /**
     * Returns true if two great circles are equivalent.
     * Two great circles are equivalent if their plane vectors are parallel.
     */
    public boolean isEquivalent(GreatCircle other) {
        return this.planeVector.isParallel(other.planeVector);
    }

    @Override
    public String toString() {
        return String.format("GreatCircle{north=%s, south=%s}",
                northernmostPoint, southernmostPoint);
    }
}
