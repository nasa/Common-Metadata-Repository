require 'orbits'
require 'spec/spec_helper'
require 'spec/mock_geometries'
require 'spec/metrics'

describe "GeometryToCoordinatesConverter" do
  context "#coordinates" do
    context "when passed a point" do
      it "allows iteration using a block" do
        point = Mock::Point.new(45, 60)
        Orbits::GeometryToCoordinatesConverter.coordinates(point, 0.1) do |coord|
          coord.should be_an Orbits::Coordinate
        end
      end

      it "returns an enumerator producing a single coordinate" do
        point = Mock::Point.new(45, 60)
        coords = Orbits::GeometryToCoordinatesConverter.coordinates(point, 0.1)
        coords.should be_lat_lons [[45, 60]]
      end
    end

    context "when passed a bounding rectangle" do
      it "allows iteration using a block" do
        rect = Mock::BoundingRectangle.new(10, 20, 30, 40)
        count = 0
        Orbits::GeometryToCoordinatesConverter.coordinates(rect, Math::PI) do |coord|
          coord.should be_an Orbits::Coordinate
        end
      end

      it "divides the rectangle along latitude and longitude lines" do
        rect = Mock::BoundingRectangle.new(0, 3, 4, 7)
        coords = Orbits::GeometryToCoordinatesConverter.coordinates(rect, Math::PI / 180)

        coords.should be_lat_lons [[3, 4], [3, 5], [3, 6],  # North edge
                                   [3, 7], [2, 7], [1, 7],  # East edge
                                   [0, 7], [0, 6], [0, 5],  # South edge
                                   [0, 4], [1, 4], [2, 4]]  # West edge
      end

      it "divides rectangles in the southern hemisphere" do
        rect = Mock::BoundingRectangle.new(-5, -2, -9, -6)
        coords = Orbits::GeometryToCoordinatesConverter.coordinates(rect, Math::PI / 180)

        coords.should be_lat_lons [[-2, -9], [-2, -8], [-2, -7],  # North edge
                                   [-2, -6], [-3, -6], [-4, -6],  # East edge
                                   [-5, -6], [-5, -7], [-5, -8],  # South edge
                                   [-5, -9], [-4, -9], [-3, -9]]  # West edge
      end

      it "divides rectangles crossing the equator and meridian" do
        rect = Mock::BoundingRectangle.new(-1.5, 1.5, -1.5, 1.5)
        coords = Orbits::GeometryToCoordinatesConverter.coordinates(rect, Math::PI / 180)
        coords.should be_lat_lons [[ 1.5, -1.5], [ 1.5, -0.5], [ 1.5,  0.5],  # North edge
                                   [ 1.5,  1.5], [ 0.5,  1.5], [-0.5,  1.5],  # East edge
                                   [0.0, -1.5 ], [0.0,  -0.5], [0.0, 0.5  ],  # Equator
                                   [-1.5,  1.5], [-1.5,  0.5], [-1.5, -0.5],  # South edge
                                   [-1.5, -1.5], [-0.5, -1.5], [ 0.5, -1.5]]  # West edge
      end

      it "divides rectangles crossing the date line" do
        rect = Mock::BoundingRectangle.new(1, 2, 178.5, -178.5)
        coords = Orbits::GeometryToCoordinatesConverter.coordinates(rect, Math::PI / 180)
        coords.should be_lat_lons [[2,  178.5], [2,  179.5], [2, -179.5],  # North edge
                                   [2, -178.5],                            # East edge
                                   [1, -178.5], [1, -179.5], [1,  179.5],  # South edge
                                   [1,  178.5]]                            # West edge
      end

      it "divides rectangles spanning more than 180 degrees of longitude" do
        rect = Mock::BoundingRectangle.new(1, 2, -120, 150)
        coords = Orbits::GeometryToCoordinatesConverter.coordinates(rect, Math::PI / 2)

        coords.should be_lat_lons [[2, -120], [2,  -30], [2,   60],  # North edge
                                   [2,  150],                        # East edge
                                   [1,  150], [1,   60], [1,  -30],  # South edge
                                   [1, -120]]                        # West edge
      end

      it "test large bounding boxes" do
        rect = Mock::BoundingRectangle.new(0, 90, 90, 180)
        coords = Orbits::GeometryToCoordinatesConverter.coordinates(rect, Math::PI / 4)
        coords.should be_lat_lons [[90,90], [90,135], [90,-180], [45,-180], [0,-180], [0,135], [0,90], [45,90]]     
      end
    end


    context "when passed a polygon" do
      it "divides polygons along great circle arcs" do
        points = [[10, 10], [10, 30], [50, 40]]
        angle = Math::PI / 18
        polygon = Mock::Polygon.new(points)
        dense_points = Orbits::GeometryToCoordinatesConverter.coordinates(polygon, angle).to_a
        dense_points.should be_separated_by_at_most angle
        dense_points.should fall_on_the_polygon_defined_by points
      end

      it "divides polygons in the southern hemisphere" do
        points = [[-10, -10], [-10, -30], [-50, -40]]
        angle = Math::PI / 18
        polygon = Mock::Polygon.new(points)
        dense_points = Orbits::GeometryToCoordinatesConverter.coordinates(polygon, angle).to_a
        dense_points.should be_separated_by_at_most angle
        dense_points.should fall_on_the_polygon_defined_by points
      end

      it "divides polygons crossing the equator" do
        points = [[-10, -10], [10, 30], [10, -20]]
        angle = Math::PI / 18
        polygon = Mock::Polygon.new(points)
        dense_points = Orbits::GeometryToCoordinatesConverter.coordinates(polygon, angle).to_a
        dense_points.should be_separated_by_at_most angle
        dense_points.should fall_on_the_polygon_defined_by points
      end

      it "divides polygons crossing the date line" do
        points = [[-10, 170], [-10, -160], [50, -170]]
        angle = Math::PI / 18
        polygon = Mock::Polygon.new(points)
        dense_points = Orbits::GeometryToCoordinatesConverter.coordinates(polygon, angle).to_a
        dense_points.length.should be < 20 # Make sure it wraps the right direction
        dense_points.should be_separated_by_at_most angle
        dense_points.should fall_on_the_polygon_defined_by points
      end

      it "divides polygons with points denser than the swath width" do
        points = [[1, 1], [1, 3], [5, 4]]
        angle = Math::PI / 18
        polygon = Mock::Polygon.new(points)
        dense_points = Orbits::GeometryToCoordinatesConverter.coordinates(polygon, angle).to_a
        dense_points.length.should be 3
        dense_points.should be_separated_by_at_most angle
        dense_points.should fall_on_the_polygon_defined_by points
      end

      it "divides polygons with many sides" do
        points = [[10, 10], [10, 30], [50, 40],
                  [51, 40], [40, 40], [43, 50],
                  [30,  0], [20, -20], [0, 0]]
        angle = Math::PI / 18
        polygon = Mock::Polygon.new(points)
        dense_points = Orbits::GeometryToCoordinatesConverter.coordinates(polygon, angle).to_a
        dense_points.should be_separated_by_at_most angle
        dense_points.should fall_on_the_polygon_defined_by points
      end

      it "divides polygons circling the north pole" do
        points = [[80, 0], [80, 120], [80, -120]]
        angle = Math::PI / 18
        polygon = Mock::Polygon.new(points)
        dense_points = Orbits::GeometryToCoordinatesConverter.coordinates(polygon, angle).to_a
        dense_points.should be_separated_by_at_most angle
        dense_points.should fall_on_the_polygon_defined_by points
      end

      it "divides polygons circling the south pole" do
        points = [[-80, 0], [-80, 120], [-80, -120]]
        angle = Math::PI / 18
        polygon = Mock::Polygon.new(points)
        dense_points = Orbits::GeometryToCoordinatesConverter.coordinates(polygon, angle).to_a
        dense_points.should be_separated_by_at_most angle
        dense_points.should fall_on_the_polygon_defined_by points
      end
    end

    context "when passed an unrecognized geometry" do
      it "raises a runtime error" do
        conversion = lambda do
          ring = "geometry"
          Orbits::GeometryToCoordinatesConverter.coordinates(ring, 1)
        end
        conversion.should raise_error(RuntimeError, /Unrecognized geometry/)
      end
    end
  end
end
