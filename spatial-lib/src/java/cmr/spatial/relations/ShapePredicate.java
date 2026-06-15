package cmr.spatial.relations;

/**
 * A functional interface for testing if a shape intersects another shape.
 * Used by the intersection testing logic in the spatial plugin.
 */
@FunctionalInterface
public interface ShapePredicate {
    /**
     * Tests if the given shape intersects the target shape.
     *
     * @param shape The shape to test
     * @return true if the shape intersects the target shape
     */
    boolean intersects(Object shape);
}
