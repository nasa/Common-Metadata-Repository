package cmr.spatial.serialize;

import cmr.spatial.shape.*;
import java.util.*;

/**
 * Deserialization of ordinates and ords-info into spatial shapes.
 *
 * This converts the serialized format (ords-info + ords) back into shape objects.
 * The ords-info is a sequence of [type, size, type, size, ...] pairs where:
 *   type - integer mapping to a shape type (1=geodetic-polygon, 2=geodetic-hole, etc)
 *   size - number of ordinates this shape uses
 * The ords is a flat sequence of all ordinates.
 */
public class OrdsInfoShapes {

    private static final double MULTIPLICATION_FACTOR = 10000000.0;

    // Type mappings - must match cmr.spatial.serialize/shape-type->integer
    private static final Map<Integer, String> INT_TO_TYPE = new HashMap<>();
    static {
        INT_TO_TYPE.put(1, "geodetic-polygon");
        INT_TO_TYPE.put(2, "geodetic-hole");
        INT_TO_TYPE.put(3, "br");
        INT_TO_TYPE.put(4, "point");
        INT_TO_TYPE.put(5, "geodetic-line-string");
        INT_TO_TYPE.put(6, "cartesian-polygon");
        INT_TO_TYPE.put(7, "cartesian-hole");
        INT_TO_TYPE.put(8, "cartesian-line-string");
    }

    /**
     * Converts stored ordinate value to double for spatial calculations.
     */
    public static double storedToOrdinate(long v) {
        return ((double) v) / MULTIPLICATION_FACTOR;
    }

    /**
     * Converts ords-info and ords into a list of shapes.
     *
     * @param ordsInfo List of type/size pairs describing each shape
     * @param ords Flat list of all ordinates
     * @return List of SpatialShape objects
     */
    public static List<SpatialShape> ordsInfoToShapes(List<?> ordsInfo, List<?> ords) {
        // Validate ordsInfo contains pairs
        if (ordsInfo == null) {
            throw new IllegalArgumentException("ordsInfo cannot be null");
        }
        if (ordsInfo.size() % 2 != 0) {
            throw new IllegalArgumentException(
                String.format("ordsInfo must contain pairs of (type,size), got odd length: %d. Contents: %s",
                    ordsInfo.size(), ordsInfo));
        }
        
        List<SpatialShape> shapes = new ArrayList<>();
        List<Double> ordinates = convertToDoubleList(ords);
        int ordsIndex = 0;

        // Process pairs of (type, size) from ordsInfo
        for (int i = 0; i < ordsInfo.size(); i += 2) {
            int intType = ((Number) ordsInfo.get(i)).intValue();
            int size = ((Number) ordsInfo.get(i + 1)).intValue();

            String type = INT_TO_TYPE.get(intType);
            if (type == null) {
                throw new IllegalArgumentException(
                    "Could not get a shape type from integer [" + intType + "]. Ords info: " + ordsInfo);
            }

            // Extract ordinates for this shape
            List<Double> shapeOrds = new ArrayList<>();
            for (int j = 0; j < size; j++) {
                shapeOrds.add(ordinates.get(ordsIndex + j));
            }
            ordsIndex += size;

            // Convert ordinates to shape and add to results
            SpatialShape shape = storedOrdsToShape(type, shapeOrds);

            // If this is a hole, add it to the rings of the last shape
            if ("geodetic-hole".equals(type) || "cartesian-hole".equals(type)) {
                if (!shapes.isEmpty() && shapes.get(shapes.size() - 1) instanceof Polygon) {
                    Polygon lastPolygon = (Polygon) shapes.get(shapes.size() - 1);
                    lastPolygon.getMutableRings().add((Ring) shape);
                }
            } else {
                shapes.add(shape);
            }
        }

        return shapes;
    }

    /**
     * Converts a type and ordinates into a spatial shape.
     */
    private static SpatialShape storedOrdsToShape(String type, List<Double> ords) {
        switch (type) {
            case "geodetic-polygon":
                return new Polygon("geodetic", new ArrayList<>(Arrays.asList(new Ring("geodetic", ords))));

            case "cartesian-polygon":
                return new Polygon("cartesian", new ArrayList<>(Arrays.asList(new Ring("cartesian", ords))));

            case "geodetic-hole":
                return new Ring("geodetic", ords, true);

            case "cartesian-hole":
                return new Ring("cartesian", ords, true);

            case "br":
                if (ords.size() != 4) {
                    throw new IllegalArgumentException("MBR requires exactly 4 ordinates, got " + ords.size());
                }
                return new Mbr(ords.get(0), ords.get(1), ords.get(2), ords.get(3));

            case "point":
                if (ords.size() != 2) {
                    throw new IllegalArgumentException("Point requires exactly 2 ordinates, got " + ords.size());
                }
                return new Point(ords.get(0), ords.get(1));

            case "geodetic-line-string":
                return new LineString("geodetic", ords);

            case "cartesian-line-string":
                return new LineString("cartesian", ords);

            default:
                throw new IllegalArgumentException("Unknown ords shape type: " + type);
        }
    }

    /**
     * Converts a list of numbers to a list of doubles, converting stored ordinates to actual values.
     */
    private static List<Double> convertToDoubleList(List<?> input) {
        List<Double> result = new ArrayList<>();
        for (Object obj : input) {
            long val = ((Number) obj).longValue();
            result.add(storedToOrdinate(val));
        }
        return result;
    }
}
