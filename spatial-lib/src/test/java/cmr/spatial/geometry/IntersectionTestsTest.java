package cmr.spatial.geometry;

import cmr.spatial.shape.Point;
import cmr.spatial.shape.Mbr;
import org.junit.Test;

import static org.junit.Assert.*;

public class IntersectionTestsTest {

    // ==================== Point-to-Point Intersection Tests ====================

    @Test
    public void testPointsIntersect_SamePoint() {
        Point p1 = new Point(10.0, 20.0);
        Point p2 = new Point(10.0, 20.0);
        assertTrue("Points at same location should intersect", PointIntersections.pointsIntersect(p1, p2));
    }

    @Test
    public void testPointsIntersect_DifferentPoints() {
        Point p1 = new Point(10.0, 20.0);
        Point p2 = new Point(11.0, 21.0);
        assertFalse("Different points should not intersect", PointIntersections.pointsIntersect(p1, p2));
    }

    @Test
    public void testPointsIntersect_ApproximatelyEqual() {
        Point p1 = new Point(10.0, 20.0);
        Point p2 = new Point(10.0 + 1e-10, 20.0 + 1e-10);  // Within DELTA
        assertTrue("Points within DELTA should be approximately equal", PointIntersections.pointsIntersect(p1, p2));
    }

    @Test
    public void testPointsIntersect_NorthPole() {
        Point p1 = new Point(0.0, 90.0);
        Point p2 = new Point(180.0, 90.0);  // Different longitude at north pole
        assertTrue("Any longitude at north pole should be equivalent", PointIntersections.pointsIntersect(p1, p2));
    }

    @Test
    public void testPointsIntersect_SouthPole() {
        Point p1 = new Point(0.0, -90.0);
        Point p2 = new Point(180.0, -90.0);  // Different longitude at south pole
        assertTrue("Any longitude at south pole should be equivalent", PointIntersections.pointsIntersect(p1, p2));
    }

    @Test
    public void testPointsIntersect_Antimeridian() {
        Point p1 = new Point(180.0, 45.0);
        Point p2 = new Point(-180.0, 45.0);  // Both on antimeridian
        assertTrue("-180 and 180 should be equivalent at same latitude", PointIntersections.pointsIntersect(p1, p2));
    }

    @Test
    public void testPointsIntersect_DifferentLatitudes() {
        Point p1 = new Point(10.0, 20.0);
        Point p2 = new Point(10.0, 21.0);  // Same longitude, different latitude
        assertFalse("Points at different latitudes should not intersect", PointIntersections.pointsIntersect(p1, p2));
    }

    // ==================== Point-to-MBR Intersection Tests ====================

    @Test
    public void testPointIntersectsMbr_PointInside() {
        Point point = new Point(10.0, 20.0);
        Mbr mbr = new Mbr(-30.0, 50.0, 30.0, -10.0);  // w, n, e, s
        assertTrue("Point inside MBR should intersect", PointMbrIntersections.pointIntersectsMbr(point, mbr));
    }

    @Test
    public void testPointIntersectsMbr_PointOutside() {
        Point point = new Point(50.0, 20.0);
        Mbr mbr = new Mbr(-30.0, 50.0, 30.0, -10.0);  // w, n, e, s (east bound is 30)
        assertFalse("Point outside MBR should not intersect", PointMbrIntersections.pointIntersectsMbr(point, mbr));
    }

    @Test
    public void testPointIntersectsMbr_PointOnEdge() {
        Point point = new Point(30.0, 20.0);
        Mbr mbr = new Mbr(-30.0, 50.0, 30.0, -10.0);  // w, n, e, s (east bound is 30)
        assertTrue("Point on edge of MBR should intersect", PointMbrIntersections.pointIntersectsMbr(point, mbr));
    }

    @Test
    public void testPointIntersectsMbr_NorthPoleTouches() {
        Point point = new Point(0.0, 90.0);
        Mbr mbr = new Mbr(-30.0, 90.0, 30.0, -10.0);  // w, n, e, s (north at 90)
        assertTrue("North pole point should intersect MBR touching north pole", 
            PointMbrIntersections.pointIntersectsMbr(point, mbr));
    }

    @Test
    public void testPointIntersectsMbr_SouthPoleTouches() {
        Point point = new Point(0.0, -90.0);
        Mbr mbr = new Mbr(-30.0, 50.0, 30.0, -90.0);  // w, n, e, s (south at -90)
        assertTrue("South pole point should intersect MBR touching south pole", 
            PointMbrIntersections.pointIntersectsMbr(point, mbr));
    }

    @Test
    public void testPointIntersectsMbr_AntimeridianCrossingMbr() {
        Point point = new Point(170.0, 20.0);  // Near antimeridian on positive side
        Mbr mbr = new Mbr(160.0, 50.0, -160.0, -10.0);  // Crosses antimeridian
        assertTrue("Point near antimeridian should intersect MBR crossing antimeridian", 
            PointMbrIntersections.pointIntersectsMbr(point, mbr));
    }

    // ==================== MBR-to-MBR Intersection Tests ====================

    @Test
    public void testMbrsIntersect_SimpleOverlap() {
        Mbr mbr1 = new Mbr(-30.0, 50.0, 30.0, -10.0);   // w, n, e, s
        Mbr mbr2 = new Mbr(0.0, 40.0, 60.0, 10.0);      // Overlaps with mbr1
        assertTrue("Overlapping MBRs should intersect", MbrIntersections.mbrsIntersect(mbr1, mbr2));
    }

    @Test
    public void testMbrsIntersect_NoOverlap() {
        Mbr mbr1 = new Mbr(-30.0, 50.0, -10.0, -10.0);
        Mbr mbr2 = new Mbr(40.0, 50.0, 60.0, 10.0);     // Completely to the east
        assertFalse("Non-overlapping MBRs should not intersect", MbrIntersections.mbrsIntersect(mbr1, mbr2));
    }

    @Test
    public void testMbrsIntersect_EdgeTouch() {
        Mbr mbr1 = new Mbr(-30.0, 50.0, 0.0, -10.0);
        Mbr mbr2 = new Mbr(0.0, 40.0, 30.0, 10.0);      // Touch at edge (lon=0)
        assertTrue("MBRs touching at edge should intersect", MbrIntersections.mbrsIntersect(mbr1, mbr2));
    }

    @Test
    public void testMbrsIntersect_PolesTouch() {
        Mbr mbr1 = new Mbr(-30.0, 90.0, 30.0, 50.0);    // Touches north pole
        Mbr mbr2 = new Mbr(0.0, 90.0, 60.0, 40.0);      // Also touches north pole
        assertTrue("MBRs touching at north pole should intersect in geodetic", 
            MbrIntersections.mbrsIntersect("geodetic", mbr1, mbr2));
    }

    @Test
    public void testMbrsIntersect_ContainedMbr() {
        Mbr mbr1 = new Mbr(-30.0, 50.0, 30.0, -10.0);
        Mbr mbr2 = new Mbr(-10.0, 30.0, 10.0, 0.0);     // Completely inside mbr1
        assertTrue("Contained MBR should intersect", MbrIntersections.mbrsIntersect(mbr1, mbr2));
    }

    @Test
    public void testMbrsIntersect_AntimeridianNonCrossing() {
        Mbr mbr1 = new Mbr(150.0, 50.0, 170.0, -10.0);
        Mbr mbr2 = new Mbr(160.0, 40.0, 180.0, 0.0);    // Overlaps, neither crosses
        assertTrue("Non-crossing MBRs near antimeridian should intersect if they overlap",
            MbrIntersections.mbrsIntersect(mbr1, mbr2));
    }

    @Test
    public void testMbrsIntersect_OneCrossesAntimeridian() {
        Mbr mbr1 = new Mbr(160.0, 50.0, -160.0, -10.0); // Crosses antimeridian
        Mbr mbr2 = new Mbr(-170.0, 40.0, -150.0, 0.0);  // On the other side
        assertTrue("MBR crossing antimeridian should intersect with MBR on far side",
            MbrIntersections.mbrsIntersect(mbr1, mbr2));
    }

    @Test
    public void testMbrsIntersect_BothCrossAntimeridian() {
        Mbr mbr1 = new Mbr(160.0, 50.0, -170.0, -10.0); // Crosses antimeridian
        Mbr mbr2 = new Mbr(170.0, 40.0, -150.0, 0.0);   // Also crosses antimeridian
        assertTrue("Both MBRs crossing antimeridian should intersect",
            MbrIntersections.mbrsIntersect(mbr1, mbr2));
    }

    // ==================== Edge Case Tests ====================

    @Test
    public void testCrossesAntimeridian() {
        Mbr mbr_crosses = new Mbr(160.0, 50.0, -160.0, -10.0);
        Mbr mbr_not_crosses = new Mbr(-30.0, 50.0, 30.0, -10.0);
        
        assertTrue("MBR with west > east should cross antimeridian",
            MbrIntersections.crossesAntimeridian(mbr_crosses));
        assertFalse("MBR with west < east should not cross antimeridian",
            MbrIntersections.crossesAntimeridian(mbr_not_crosses));
    }

    @Test
    public void testSplitAcrossAntimeridian() {
        Mbr mbr_crosses = new Mbr(160.0, 50.0, -160.0, -10.0);
        var parts = MbrIntersections.splitAcrossAntimeridian(mbr_crosses);
        
        assertEquals("Crossing MBR should split into 2 parts", 2, parts.size());
        assertEquals("First part should go from west to 180", 160.0, parts.get(0).getWest(), 0.0);
        assertEquals("First part should go from west to 180", 180.0, parts.get(0).getEast(), 0.0);
        assertEquals("Second part should go from -180 to east", -180.0, parts.get(1).getWest(), 0.0);
        assertEquals("Second part should go from -180 to east", -160.0, parts.get(1).getEast(), 0.0);
    }

    @Test
    public void testSplitAcrossAntimeridian_NoCross() {
        Mbr mbr_not_crosses = new Mbr(-30.0, 50.0, 30.0, -10.0);
        var parts = MbrIntersections.splitAcrossAntimeridian(mbr_not_crosses);
        
        assertEquals("Non-crossing MBR should return 1 part", 1, parts.size());
        assertEquals("Part should be the same as original", mbr_not_crosses, parts.get(0));
    }
}
