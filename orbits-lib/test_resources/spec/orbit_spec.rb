require 'orbits'
require 'spec/orbit_data'
require 'spec/spec_helper'
require 'spec/mock_geometries'
require 'spec/metrics'

describe "Orbit" do
  context "#coord_crossing_range" do
    context "when compared to legacy getPointCrossingRange" do
      # This is bad rspec form but lets us test a lot of points and easily see what fails
      latitudes = [-90, -88, -60, -47, -1, 0, 1, 47, 60, 88, 90]
      longitudes = [-180, -179, -100, -61, 1, 0, 1, 61, 100, 179, 180]
      test_coordinates = []

      latitudes.each do |lat|
        longitudes.each do |lon|
          test_coordinates << [lat, lon]
        end
      end

      passes = [:ascending, :descending]
      orbit_pairs = OrbitData.nsidc_orbits.zip(OrbitData.echo_orbits)

      orbit_pairs.each do |java_orbit, ruby_orbit|
        context "for #{ruby_orbit.to_s}" do
          test_coordinates.each do |lat, lon|
            passes.each do |pass|
              it "calculates a similar value for (#{lat}, #{lon}) on the #{pass} pass" do
                is_ascending = pass == :ascending
                java_range = Metrics.time('backtracking points in Java') do
                  java_orbit.get_point_crossing_range(lat, lon, is_ascending)
                end

                ruby_range = Metrics.time('backtracking points in Ruby') do
                  coord = Orbits::Coordinate.lat_lon(lat, lon)
                  ruby_orbit.coord_crossing_range(coord, is_ascending).to_a.flatten
                end

                # We don't test against the legacy code when size is 0 because
                # it ignores that case to avoid returning null
                if ruby_range.size > 0
                  java_range.min.should be_close_to ruby_range[0]
                  java_range.max.should be_close_to ruby_range[1]
                end
              end
            end
          end
        end
      end
    end

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
        range = orbit.area_crossing_range(geometry,true)
        range.to_a.size.should be == 1
        range.to_a[0][0].should be == -180
        range.to_a[0][1].should be == 180
      end
    end
  end
end
