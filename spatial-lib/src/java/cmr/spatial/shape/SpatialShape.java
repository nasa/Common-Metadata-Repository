package cmr.spatial.shape;

/**
 * Base interface for spatial shapes used by the Java implementation.
 * These are minimal POJOs used internally for shape deserialization and intersection testing.
 */
public interface SpatialShape {
    String getType();
}
