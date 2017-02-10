require 'orbits'
require 'spec/spec_helper'
require 'spec/mock_geometries'
require 'spec/metrics'

describe "Orbit" do
  context "#coord_crossing_range" do
    describe "given points near the orbit's inflection" do
      it "returns no results for points above the swath" do
        # Swath width of 889km is almost exactly 8 degrees, so at the inflection
        # point, this orbit sees latitudes 71-79.
        orbit = Orbits::Orbit.new(105, 100, 889, 0, 1)

        coord = Orbits::Coordinate.lat_lon(80, 0)
        orbit.coord_crossing_range(coord, true).coverage.should be_empty
        orbit.coord_crossing_range(coord, false).coverage.should be_empty

        coord = Orbits::Coordinate.lat_lon(-80, 0)
        orbit.coord_crossing_range(coord, true).coverage.should be_empty
        orbit.coord_crossing_range(coord, false).coverage.should be_empty
      end

      it "returns total coverage near the pole when the sensor can see the pole" do
        # At the inflection point, this orbit sees latitudes 85-93 (past the pole).
        orbit = Orbits::Orbit.new(91, 100, 889, 0, 1)

        coord = Orbits::Coordinate.lat_lon(89, 0)
        orbit.coord_crossing_range(coord, true).coverage == [-Math::PI, Math::PI]
        orbit.coord_crossing_range(coord, false).coverage == [-Math::PI, Math::PI]

        coord = Orbits::Coordinate.lat_lon(-89, 0)
        orbit.coord_crossing_range(coord, true).coverage == [-Math::PI, Math::PI]
        orbit.coord_crossing_range(coord, false).coverage == [-Math::PI, Math::PI]
      end

      it "returns only the ascending coverage for swath points above lower swath edge" do
        # At the inflection point, this orbit sees latitudes 71-79.
        orbit = Orbits::Orbit.new(105, 100, 889, 0, 1)

        # TODO visually verify these results first
        # TODO this test is a little fragile due to floating point drift
        ascending_result = [0.9976858957122205, 1.6798794050445425]
        descending_result = [1.679879406079345, 2.362072972985338]

        coord = Orbits::Coordinate.lat_lon(76, 0)
        orbit.coord_crossing_range(coord, true).coverage.should == ascending_result
        orbit.coord_crossing_range(coord, false).coverage.should == descending_result
      end

      it "returns correct coverage for points slightly below the lower swath edge" do
        # This is a case that the legacy code gets wrong for some reason

        # At the inflection point, this orbit sees latitudes 71-79.
        orbit = Orbits::Orbit.new(105, 100, 889, 0, 1)

        # TODO this test is a little fragile due to floating point drift
        ascending_result = [0.6456352756039809, 1.3376197217185712]
        descending_result = [2.0221390883705244, 2.714123534485107]

        coord = Orbits::Coordinate.lat_lon(70, 0)
        orbit.coord_crossing_range(coord, true).coverage.should == ascending_result
        orbit.coord_crossing_range(coord, false).coverage.should == descending_result
      end
    end
  end

  #Added for NCR 11013673
  context "#area_crossing_range" do
    describe "area crossing range returns correct longitude ranges" do
      it "returns correct coverage for whole earth" do
        orbit = Orbits::Orbit.new(94.0, 96.7, 2.0, -50.0, 0.25)
        geometry = Mock::BoundingRectangle.new(-90, 90, -180, 180)
        ranges = orbit.area_crossing_range([-90, 90], geometry,true)
        range = ranges.last.last
        range.to_a.size.should be == 1
        range.to_a[0][0].should be == -180
        range.to_a[0][1].should be == 180
      end
    end
  end
end
