package cmr.spatial.shape;

/**
 * Java representation of a Circle shape.
 * A circle is defined by a center point and a radius in meters.
 */
public class Circle implements SpatialShape {
    private final Point center;
    private final double radius;

    public Circle(Point center, double radius) {
        this.center = center;
        this.radius = radius;
    }

    public Point getCenter() {
        return center;
    }

    public double getRadius() {
        return radius;
    }

    @Override
    public String getType() {
        return "circle";
    }

    @Override
    public String toString() {
        return String.format("Circle{center=%s, radius=%.2f}", center, radius);
    }
}
