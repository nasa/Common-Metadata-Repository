package cmr.spatial.test;

import cmr.spatial.geometry.*;
import cmr.spatial.java.*;
import cmr.spatial.relations.ShapeIntersections;
import cmr.spatial.relations.ShapePredicate;
import cmr.spatial.serialize.OrdsInfoShapes;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

/**
 * Tests to verify that all ES plugin shape intersections work in pure Java
 * without any Clojure delegation.
 * 
 * The ES plugin uses OrdsInfoShapes to deserialize shapes from stored format,
 * which creates Java shapes (Point, Mbr, LineString, Ring, Polygon).
 * All shape-to-shape intersections must work without calling Clojure.
 */
public class ESPluginJavaIntersectionsTest {

    @Test
    public void testPointIntersections() {
        Point p1 = new Point(0, 0);
        Point p2 = new Point(0, 0);
        Point p3 = new Point(10, 10);
        
        // Point-to-Point
        ShapePredicate pred = ShapeIntersections.createIntersectsFn(p1);
        assertTrue("Point intersects itself", pred.test(p2));
        assertFalse("Point does not intersect distant point", pred.test(p3));
        
        // Point-to-Mbr
        Mbr mbr = new Mbr(-1, 1, 1, -1);
        assertTrue("Point intersects containing MBR", pred.test(mbr));
        
        // Point-to-LineString
        LineString line = new LineString("geodetic", Arrays.asList(-1.0, 0.0, 1.0, 0.0));
        assertTrue("Point intersects line through it", pred.test(line));
        
        // Point-to-Ring
        Ring ring = new Ring("geodetic", Arrays.asList(-2.0, -2.0, 2.0, -2.0, 2.0, 2.0, -2.0, 2.0, -2.0, -2.0));
        assertTrue("Point intersects containing ring", pred.test(ring));
        
        // Point-to-Polygon
        Polygon polygon = new Polygon("geodetic", Arrays.asList(ring));
        assertTrue("Point intersects containing polygon", pred.test(polygon));
    }

    @Test
    public void testMbrIntersections() {
        Mbr mbr1 = new Mbr(-1, 1, 1, -1);
        Mbr mbr2 = new Mbr(0, 2, 2, 0);
        
        ShapePredicate pred = ShapeIntersections.createIntersectsFn(mbr1);
        
        // Mbr-to-Point
        Point p = new Point(0, 0);
        assertTrue("MBR intersects contained point", pred.test(p));
        
        // Mbr-to-Mbr
        assertTrue("MBR intersects overlapping MBR", pred.test(mbr2));
        
        // Mbr-to-LineString
        LineString line = new LineString("geodetic", Arrays.asList(-0.5, 0.0, 0.5, 0.0));
        assertTrue("MBR intersects line through it", pred.test(line));
        
        // Mbr-to-Ring
        Ring ring = new Ring("geodetic", Arrays.asList(-0.5, -0.5, 0.5, -0.5, 0.5, 0.5, -0.5, 0.5, -0.5, -0.5));
        assertTrue("MBR intersects ring inside it", pred.test(ring));
        
        // Mbr-to-Polygon
        Polygon polygon = new Polygon("geodetic", Arrays.asList(ring));
        assertTrue("MBR intersects polygon inside it", pred.test(polygon));
    }

    @Test
    public void testLineStringIntersections() {
        LineString line1 = new LineString("geodetic", Arrays.asList(-1.0, 0.0, 1.0, 0.0));
        
        ShapePredicate pred = ShapeIntersections.createIntersectsFn(line1);
        
        // LineString-to-Point
        Point p = new Point(0, 0);
        assertTrue("LineString intersects point on it", pred.test(p));
        
        // LineString-to-Mbr
        Mbr mbr = new Mbr(-0.5, 0.5, 0.5, -0.5);
        assertTrue("LineString intersects MBR it passes through", pred.test(mbr));
        
        // LineString-to-LineString
        LineString line2 = new LineString("geodetic", Arrays.asList(0.0, -1.0, 0.0, 1.0));
        assertTrue("LineString intersects crossing line", pred.test(line2));
        
        // LineString-to-Ring
        Ring ring = new Ring("geodetic", Arrays.asList(-2.0, -2.0, 2.0, -2.0, 2.0, 2.0, -2.0, 2.0, -2.0, -2.0));
        assertTrue("LineString intersects containing ring", pred.test(ring));
        
        // LineString-to-Polygon
        Polygon polygon = new Polygon("geodetic", Arrays.asList(ring));
        assertTrue("LineString intersects containing polygon", pred.test(polygon));
    }

    @Test
    public void testRingIntersections() {
        Ring ring1 = new Ring("geodetic", Arrays.asList(-2.0, -2.0, 2.0, -2.0, 2.0, 2.0, -2.0, 2.0, -2.0, -2.0));
        
        ShapePredicate pred = ShapeIntersections.createIntersectsFn(ring1);
        
        // Ring-to-Point
        Point p = new Point(0, 0);
        assertTrue("Ring intersects point inside it", pred.test(p));
        
        // Ring-to-Mbr
        Mbr mbr = new Mbr(-1, 1, 1, -1);
        assertTrue("Ring intersects MBR inside it", pred.test(mbr));
        
        // Ring-to-LineString
        LineString line = new LineString("geodetic", Arrays.asList(-1.0, 0.0, 1.0, 0.0));
        assertTrue("Ring intersects line inside it", pred.test(line));
        
        // Ring-to-Ring
        Ring ring2 = new Ring("geodetic", Arrays.asList(-1.0, -1.0, 1.0, -1.0, 1.0, 1.0, -1.0, 1.0, -1.0, -1.0));
        assertTrue("Ring intersects ring inside it", pred.test(ring2));
        
        // Ring-to-Polygon
        Polygon polygon = new Polygon("geodetic", Arrays.asList(ring2));
        assertTrue("Ring intersects polygon inside it", pred.test(polygon));
    }

    @Test
    public void testPolygonIntersections() {
        Ring outerRing = new Ring("geodetic", Arrays.asList(-2.0, -2.0, 2.0, -2.0, 2.0, 2.0, -2.0, 2.0, -2.0, -2.0));
        Polygon polygon1 = new Polygon("geodetic", Arrays.asList(outerRing));
        
        ShapePredicate pred = ShapeIntersections.createIntersectsFn(polygon1);
        
        // Polygon-to-Point
        Point p = new Point(0, 0);
        assertTrue("Polygon intersects point inside it", pred.test(p));
        
        // Polygon-to-Mbr
        Mbr mbr = new Mbr(-1, 1, 1, -1);
        assertTrue("Polygon intersects MBR inside it", pred.test(mbr));
        
        // Polygon-to-LineString
        LineString line = new LineString("geodetic", Arrays.asList(-1.0, 0.0, 1.0, 0.0));
        assertTrue("Polygon intersects line inside it", pred.test(line));
        
        // Polygon-to-Ring
        Ring ring = new Ring("geodetic", Arrays.asList(-1.0, -1.0, 1.0, -1.0, 1.0, 1.0, -1.0, 1.0, -1.0, -1.0));
        assertTrue("Polygon intersects ring inside it", pred.test(ring));
        
        // Polygon-to-Polygon
        Polygon polygon2 = new Polygon("geodetic", Arrays.asList(ring));
        assertTrue("Polygon intersects polygon inside it", pred.test(polygon2));
    }

    @Test
    public void testOrdsInfoShapesDeserializationWorkflow() {
        // Simulate ES plugin workflow: deserialize shapes from stored format
        
        // Point shape
        List<Integer> ordsInfo1 = Arrays.asList(4, 2);  // type 4 = point, size 2
        List<Long> ords1 = Arrays.asList(0L, 0L);  // lon=0, lat=0
        List<SpatialShape> shapes1 = OrdsInfoShapes.ordsInfoToShapes(ordsInfo1, ords1);
        assertEquals(1, shapes1.size());
        assertTrue(shapes1.get(0) instanceof Point);
        
        // Geodetic polygon shape
        List<Integer> ordsInfo2 = Arrays.asList(1, 10);  // type 1 = geodetic-polygon, size 10
        List<Long> ords2 = Arrays.asList(
            -20000000L, -20000000L,  // -2, -2
            20000000L, -20000000L,   //  2, -2
            20000000L, 20000000L,    //  2,  2
            -20000000L, 20000000L,   // -2,  2
            -20000000L, -20000000L   // -2, -2 (closing)
        );
        List<SpatialShape> shapes2 = OrdsInfoShapes.ordsInfoToShapes(ordsInfo2, ords2);
        assertEquals(1, shapes2.size());
        assertTrue(shapes2.get(0) instanceof Polygon);
        
        // Verify intersection works end-to-end
        ShapePredicate pred = ShapeIntersections.createIntersectsFn(shapes1.get(0));
        assertTrue("Deserialized shapes can intersect", pred.test(shapes2.get(0)));
    }
}
