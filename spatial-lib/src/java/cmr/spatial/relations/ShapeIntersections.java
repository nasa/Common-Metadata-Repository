package cmr.spatial.relations;

import cmr.spatial.shape.*;
import cmr.spatial.geometry.PointIntersections;
import cmr.spatial.geometry.PointMbrIntersections;
import cmr.spatial.geometry.MbrIntersections;
import cmr.spatial.geometry.LineStringIntersections;
import cmr.spatial.geometry.RingIntersections;
import cmr.spatial.geometry.PolygonIntersections;
import cmr.spatial.geometry.CircleIntersections;

/**
 * Creates intersection testing functions for spatial shapes.
 * Routes shape intersection tests to appropriate Java or Clojure implementations.
 * 
 * Implemented in Java:
 * - Point-to-Point, Point-to-Mbr
 * - Mbr-to-Mbr
 * - LineString, Ring, Polygon, and Circle intersections
 * 
 * Delegated to Clojure:
 * - Arc and LineSegment intersection tests (complex geometry algorithms)
 * - Winding number algorithm for point-in-polygon
 */
public class ShapeIntersections {

    /**
     * Creates a ShapePredicate that tests if shapes intersect with the given shape.
     * Dispatches to appropriate intersection logic based on shape types.
     *
     * Supports: Point, Mbr, LineString, Ring, Polygon, Circle
     * 
     * For Clojure shape records (GeodeticRing, CartesianRing, etc.), use
     * the Clojure API (relations/shape->intersects-fn) instead.
     *
     * @param shape The target shape to test intersections against
     * @return A ShapePredicate that returns true if another shape intersects this one
     * @throws UnsupportedOperationException if shape type is not supported
     */
    public static ShapePredicate createIntersectsFn(Object shape) {
        if (shape instanceof Point) {
            return createPointIntersectsFn((Point) shape);
        } else if (shape instanceof Mbr) {
            return createMbrIntersectsFn((Mbr) shape);
        } else if (shape instanceof LineString) {
            return createLineStringIntersectsFn((LineString) shape);
        } else if (shape instanceof Ring) {
            return createRingIntersectsFn((Ring) shape);
        } else if (shape instanceof Polygon) {
            return createPolygonIntersectsFn((Polygon) shape);
        } else if (shape instanceof Circle) {
            return createCircleIntersectsFn((Circle) shape);
        } else {
            throw new UnsupportedOperationException(
                "createIntersectsFn not implemented for shape type: " + shape.getClass().getName() +
                ". Use Clojure API (relations/shape->intersects-fn) for Clojure shape records.");
        }
    }

    /**
     * Creates a predicate for Point intersection testing.
     * Handles Point-to-Point, Point-to-Mbr, Point-to-LineString, Point-to-Ring, and Point-to-Polygon intersections.
     */
    private static ShapePredicate createPointIntersectsFn(Point point) {
        return otherShape -> {
            if (otherShape instanceof Point) {
                return PointIntersections.pointsIntersect(point, (Point) otherShape);
            } else if (otherShape instanceof Mbr) {
                // Point intersects Mbr if Mbr covers the point
                return PointMbrIntersections.pointIntersectsMbr(point, (Mbr) otherShape);
            } else if (otherShape instanceof LineString) {
                // Point intersects LineString if LineString covers the point
                return LineStringIntersections.coversPoint((LineString) otherShape, point);
            } else if (otherShape instanceof Ring) {
                // Point intersects Ring if Ring covers the point
                Object javaRing = RingIntersections.createJavaRing((Ring) otherShape);
                return RingIntersections.coversPoint(javaRing, point);
            } else if (otherShape instanceof Polygon) {
                // Point intersects Polygon if Polygon covers the point
                return PolygonIntersections.coversPoint((Polygon) otherShape, point);
            } else if (otherShape instanceof Circle) {
                // Point intersects Circle if Circle covers the point
                return CircleIntersections.coversPoint((Circle) otherShape, point);
            } else {
                throw new UnsupportedOperationException(
                    "Point intersection not implemented for shape type: " + otherShape.getClass().getName());
            }
        };
    }

    /**
     * Creates a predicate for Mbr intersection testing.
     * Handles Mbr-to-Point, Mbr-to-Mbr, Mbr-to-LineString, Mbr-to-Ring, and Mbr-to-Polygon intersections.
     */
    private static ShapePredicate createMbrIntersectsFn(Mbr mbr) {
        return otherShape -> {
            if (otherShape instanceof Point) {
                // Mbr intersects Point if Mbr covers the point
                return PointMbrIntersections.pointIntersectsMbr((Point) otherShape, mbr);
            } else if (otherShape instanceof Mbr) {
                return MbrIntersections.mbrsIntersect(mbr, (Mbr) otherShape);
            } else if (otherShape instanceof LineString) {
                // Mbr intersects LineString if LineString intersects the Mbr
                return LineStringIntersections.intersectsMbr((LineString) otherShape, mbr);
            } else if (otherShape instanceof Ring) {
                // Mbr intersects Ring if Ring intersects the Mbr
                Object javaRing = RingIntersections.createJavaRing((Ring) otherShape);
                return RingIntersections.intersectsBr(javaRing, mbr);
            } else if (otherShape instanceof Polygon) {
                // Mbr intersects Polygon if Polygon intersects the Mbr
                return PolygonIntersections.intersectsBr((Polygon) otherShape, mbr);
            } else if (otherShape instanceof Circle) {
                // Mbr intersects Circle if Circle intersects the Mbr
                return CircleIntersections.intersectsBr((Circle) otherShape, mbr);
            } else {
                // Delegate to Clojure for other types (should not happen with ES plugin)
                throw new UnsupportedOperationException(
                    "Intersection not implemented for shape type: " + otherShape.getClass().getName());
            }
        };
    }

    /**
     * Creates a predicate for LineString intersection testing.
     * Handles LineString-to-Point, LineString-to-Mbr, LineString-to-LineString, 
     * LineString-to-Ring, and LineString-to-Polygon intersections.
     */
    private static ShapePredicate createLineStringIntersectsFn(LineString lineString) {
        return otherShape -> {
            if (otherShape instanceof Point) {
                return LineStringIntersections.coversPoint(lineString, (Point) otherShape);
            } else if (otherShape instanceof Mbr) {
                return LineStringIntersections.intersectsMbr(lineString, (Mbr) otherShape);
            } else if (otherShape instanceof LineString) {
                return LineStringIntersections.intersectsLineString(lineString, (LineString) otherShape);
            } else if (otherShape instanceof Ring) {
                // LineString intersects Ring if Ring intersects the LineString
                Object javaRing = RingIntersections.createJavaRing((Ring) otherShape);
                return RingIntersections.intersectsLineString(javaRing, lineString);
            } else if (otherShape instanceof Polygon) {
                // LineString intersects Polygon if Polygon intersects the LineString
                return PolygonIntersections.intersectsLineString((Polygon) otherShape, lineString);
            } else if (otherShape instanceof Circle) {
                // LineString intersects Circle if Circle intersects the LineString
                return CircleIntersections.intersectsLineString((Circle) otherShape, lineString);
            } else {
                // Delegate to Clojure for other types (should not happen with ES plugin)
                throw new UnsupportedOperationException(
                    "Intersection not implemented for shape type: " + otherShape.getClass().getName());
            }
        };
    }

    /**
     * Creates a predicate for Ring intersection testing.
     * Handles Ring-to-Point, Ring-to-Mbr, Ring-to-LineString, Ring-to-Ring, 
     * and Ring-to-Polygon intersections.
     */
    private static ShapePredicate createRingIntersectsFn(Ring ring) {
        Object javaRing = RingIntersections.createJavaRing(ring);
        return otherShape -> {
            if (otherShape instanceof Point) {
                return RingIntersections.coversPoint(javaRing, (Point) otherShape);
            } else if (otherShape instanceof Mbr) {
                return RingIntersections.intersectsBr(javaRing, (Mbr) otherShape);
            } else if (otherShape instanceof LineString) {
                return RingIntersections.intersectsLineString(javaRing, (LineString) otherShape);
            } else if (otherShape instanceof Ring) {
                Object otherJavaRing = RingIntersections.createJavaRing((Ring) otherShape);
                return RingIntersections.intersectsRing(javaRing, otherJavaRing);
            } else if (otherShape instanceof Polygon) {
                // Ring intersects Polygon if Polygon intersects the Ring
                return PolygonIntersections.intersectsRing((Polygon) otherShape, ring);
            } else if (otherShape instanceof Circle) {
                // Ring intersects Circle if Circle intersects the Ring
                return CircleIntersections.intersectsRing((Circle) otherShape, ring);
            } else {
                // Delegate to Clojure for other types (should not happen with ES plugin)
                throw new UnsupportedOperationException(
                    "Intersection not implemented for shape type: " + otherShape.getClass().getName());
            }
        };
    }

    /**
     * Creates a predicate for Polygon intersection testing.
     * Handles Polygon-to-Point, Polygon-to-Mbr, Polygon-to-LineString, Polygon-to-Ring,
     * Polygon-to-Polygon, and Polygon-to-Circle intersections.
     */
    private static ShapePredicate createPolygonIntersectsFn(Polygon polygon) {
        return otherShape -> {
            if (otherShape instanceof Point) {
                return PolygonIntersections.coversPoint(polygon, (Point) otherShape);
            } else if (otherShape instanceof Mbr) {
                return PolygonIntersections.intersectsBr(polygon, (Mbr) otherShape);
            } else if (otherShape instanceof LineString) {
                return PolygonIntersections.intersectsLineString(polygon, (LineString) otherShape);
            } else if (otherShape instanceof Ring) {
                return PolygonIntersections.intersectsRing(polygon, (Ring) otherShape);
            } else if (otherShape instanceof Polygon) {
                return PolygonIntersections.intersectsPolygon(polygon, (Polygon) otherShape);
            } else if (otherShape instanceof Circle) {
                // Polygon intersects Circle if Circle intersects the Polygon
                return CircleIntersections.intersectsPolygon((Circle) otherShape, polygon);
            } else {
                // Delegate to Clojure for other types (should not happen with ES plugin)
                throw new UnsupportedOperationException(
                    "Intersection not implemented for shape type: " + otherShape.getClass().getName());
            }
        };
    }

    /**
     * Creates a predicate for Circle intersection testing.
     * Handles Circle-to-Point, Circle-to-Mbr, Circle-to-LineString, Circle-to-Ring,
     * and Circle-to-Polygon intersections.
     */
    private static ShapePredicate createCircleIntersectsFn(Circle circle) {
        return otherShape -> {
            if (otherShape == null) {
                throw new IllegalArgumentException("Intersection not implemented for null shape");
            }
            
            if (otherShape instanceof Point) {
                return CircleIntersections.coversPoint(circle, (Point) otherShape);
            } else if (otherShape instanceof Mbr) {
                return CircleIntersections.intersectsBr(circle, (Mbr) otherShape);
            } else if (otherShape instanceof LineString) {
                return CircleIntersections.intersectsLineString(circle, (LineString) otherShape);
            } else if (otherShape instanceof Ring) {
                return CircleIntersections.intersectsRing(circle, (Ring) otherShape);
            } else if (otherShape instanceof Polygon) {
                return CircleIntersections.intersectsPolygon(circle, (Polygon) otherShape);
            } else if (otherShape instanceof Circle) {
                // Circle-to-circle: convert both to polygons
                Polygon circlePoly = CircleIntersections.circleToPolygon(circle);
                return CircleIntersections.intersectsPolygon((Circle) otherShape, circlePoly);
            } else {
                // Delegate to Clojure for other types
                throw new UnsupportedOperationException(
                    "Intersection not implemented for shape type: " + otherShape.getClass().getName());
            }
        };
    }

    // Clojure code uses protocol dispatch (relations.clj:shape->intersects-fn)
    // and never calls this Java API with Clojure shape records.
    // ES plugin only uses Java shapes (Point, Mbr, LineString, Ring, Polygon, Circle).
    // Therefore, no Clojure delegation is needed.
}
