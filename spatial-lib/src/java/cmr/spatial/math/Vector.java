package cmr.spatial.math;

/**
 * Represents a 3D vector for spherical geometry calculations.
 * Immutable value class.
 */
public final class Vector {
    private final double x;
    private final double y;
    private final double z;

    public Vector(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    /**
     * Calculates the length (magnitude) of this vector.
     */
    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    /**
     * Returns a normalized version of this vector (length = 1).
     * Throws IllegalArgumentException if vector length is 0.
     */
    public Vector normalize() {
        double len = length();
        if (len == 0.0) {
            throw new IllegalArgumentException("Cannot normalize a vector with length 0");
        }
        return new Vector(x / len, y / len, z / len);
    }

    /**
     * Calculates the cross product of this vector with another.
     * Returns a vector perpendicular to both input vectors.
     */
    public Vector crossProduct(Vector other) {
        double px = this.y * other.z - other.y * this.z;
        double py = this.z * other.x - other.z * this.x;
        double pz = this.x * other.y - this.y * other.x;
        return new Vector(px, py, pz);
    }

    /**
     * Returns a vector pointing in the opposite direction.
     */
    public Vector opposite() {
        return new Vector(-x, -y, -z);
    }

    /**
     * Vector equality tolerance: roughly equivalent to 0.9 meters on Earth's surface.
     */
    public static final double VECTOR_EQUAL_DELTA = 0.0000001;

    /**
     * Tests if two vectors are approximately equal within default tolerance.
     */
    public boolean approxEquals(Vector other) {
        return approxEquals(other, VECTOR_EQUAL_DELTA);
    }

    /**
     * Tests if two vectors are approximately equal within specified tolerance.
     */
    public boolean approxEquals(Vector other, double delta) {
        return MathUtils.doubleApprox(this.x, other.x, delta)
            && MathUtils.doubleApprox(this.y, other.y, delta)
            && MathUtils.doubleApprox(this.z, other.z, delta);
    }

    /**
     * Returns true if two vectors are parallel (point in same or opposite directions).
     * Assumes both vectors are normalized.
     */
    public boolean isParallel(Vector other) {
        return this.approxEquals(other) || this.opposite().approxEquals(other);
    }

    @Override
    public String toString() {
        return String.format("Vector{x=%.6f, y=%.6f, z=%.6f}", x, y, z);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Vector)) return false;
        Vector other = (Vector) obj;
        return Double.compare(x, other.x) == 0
            && Double.compare(y, other.y) == 0
            && Double.compare(z, other.z) == 0;
    }

    @Override
    public int hashCode() {
        int result = 17;
        long temp = Double.doubleToLongBits(x);
        result = 31 * result + (int)(temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(y);
        result = 31 * result + (int)(temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(z);
        result = 31 * result + (int)(temp ^ (temp >>> 32));
        return result;
    }
}
