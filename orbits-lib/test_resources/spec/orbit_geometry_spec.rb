require 'orbits'
require 'spec/spec_helper'
require 'spec/mock_geometries'
require 'orbits/orbit_geometry'


describe Orbits::OrbitGeometry do
  
  context "#ground_track" do
    describe "ground track returns correct latitude" do

      it "returns correct latitude for retrograde orbit" do
        orbit_geometry = Orbits::OrbitGeometry.new(Orbits::Orbit.new(98.0, 97.87, 390, -90, 1.0))
        [-2,-1,0,1,2].each do |orbit_num|
          orbit_geometry.ground_track(-71.2686, orbit_num * 97.87 + 14.4675).lat.should be_between(0,82)
          orbit_geometry.ground_track(-71.2686, orbit_num * 97.87 + 24.4675).lat.should be_close_to 82
          orbit_geometry.ground_track(-71.2686, orbit_num * 97.87 + 34.4675).lat.should be_between(0,82)
          orbit_geometry.ground_track(-71.2686, orbit_num * 97.87 + 48.935).lat.should be_close_to 0
          orbit_geometry.ground_track(-71.2686, orbit_num * 97.87 + 58.935).lat.should be_between(-82,0)
          orbit_geometry.ground_track(-71.2686, orbit_num * 97.87 + 73.4025).lat.should be_close_to -82
          orbit_geometry.ground_track(-71.2686, orbit_num * 97.87 + 83.4025).lat.should be_between(-82,0)
          orbit_geometry.ground_track(-71.2686, orbit_num * 97.87 + 97.87).lat.should be_close_to 0
        end
      end

      it "returns correct latitude for prograde orbit" do
        orbit_geometry = Orbits::OrbitGeometry.new(Orbits::Orbit.new(82.0, 97.87, 390, -90, 1.0))
        [-2,-1,0,1,2].each do |orbit_num|
          orbit_geometry.ground_track(-71.2686, orbit_num * 97.87 + 14.4675).lat.should be_between(0,82)
          orbit_geometry.ground_track(-71.2686, orbit_num * 97.87 + 24.4675).lat.should be_close_to 82
          orbit_geometry.ground_track(-71.2686, orbit_num * 97.87 + 34.4675).lat.should be_between(0,82)
          orbit_geometry.ground_track(-71.2686, orbit_num * 97.87 + 48.935).lat.should be_close_to 0
          orbit_geometry.ground_track(-71.2686, orbit_num * 97.87 + 58.935).lat.should be_between(-82,0)
          orbit_geometry.ground_track(-71.2686, orbit_num * 97.87 + 73.4025).lat.should be_close_to -82
          orbit_geometry.ground_track(-71.2686, orbit_num * 97.87 + 83.4025).lat.should be_between(-82,0)
          orbit_geometry.ground_track(-71.2686, orbit_num * 97.87 + 97.87).lat.should be_close_to 0
        end
      end

      it "returns correct latitude for equitorial orbit" do
        orbit_geometry = Orbits::OrbitGeometry.new(Orbits::Orbit.new(0, 97.87, 390, -90, 1.0))
        [-2,-1,0,1,2].each do |orbit_num|
          orbit_geometry.ground_track(-71.2686, orbit_num * 97.87 + 14.4675).lat.should be_close_to 0
          orbit_geometry.ground_track(-71.2686, orbit_num * 97.87 + 24.4675).lat.should be_close_to 0
          orbit_geometry.ground_track(-71.2686, orbit_num * 97.87 + 34.4675).lat.should be_close_to 0
          orbit_geometry.ground_track(-71.2686, orbit_num * 97.87 + 48.935).lat.should be_close_to 0
          orbit_geometry.ground_track(-71.2686, orbit_num * 97.87 + 58.935).lat.should be_close_to 0
          orbit_geometry.ground_track(-71.2686, orbit_num * 97.87 + 73.4025).lat.should be_close_to 0
          orbit_geometry.ground_track(-71.2686, orbit_num * 97.87 + 83.4025).lat.should be_close_to 0
          orbit_geometry.ground_track(-71.2686, orbit_num * 97.87 + 97.87).lat.should be_close_to 0
        end
      end
    end

    describe "ground track returns correct longitude" do
      it "returns correct longitude for retrograde orbit" do
        orbit_geometry = Orbits::OrbitGeometry.new(Orbits::Orbit.new(98.15, 98.88, 1450.0, -90, 0.5))
        orbit_geometry.ground_track(-158.1, 98.88/2).lon.should be_close_to 9.54
        orbit_geometry.ground_track(-158.1, 98.88).lon.should be_close_to 177.16
        orbit_geometry.ground_track(177.19, 98.88).lon.should be_close_to 152.43
        orbit_geometry.ground_track(152.43, 98.88/2).lon.should be_close_to -39.9
      end

      it "returns correct longitude for prograde orbit" do
        orbit_geometry1 = Orbits::OrbitGeometry.new(Orbits::Orbit.new(98.15, 98.88, 1450.0, -90, 0.5))
        orbit_geometry2 = Orbits::OrbitGeometry.new(Orbits::Orbit.new(81.85, 98.88, 1450.0, -90, 0.5))
        orbit_geometry1.ground_track(-158.1, 98.88/2).lon.should be_close_to orbit_geometry2.ground_track(-158.1, 98.88/2).lon
        orbit_geometry2.ground_track(-158.1, 98.88/10).lon.should  > orbit_geometry2.ground_track(-158.1, 0).lon
        orbit_geometry1.ground_track(-158.1, 98.88/4).lon.should_not == orbit_geometry2.ground_track(-158.1, 98.88/4).lon
        orbit_geometry1.ground_track(-158.1, 98.88).lon.should be_close_to orbit_geometry2.ground_track(-158.1, 98.88).lon
      end

      it "returns correct longitude for polar orbit" do
        orbit = orbit_geometry = Orbits::OrbitGeometry.new(Orbits::Orbit.new(90.0, 90, 2.0, -50.0, 0.25))
        orbit_geometry.ground_track(0,10).lon.should be_close_to -((360.0/Orbits::SOLAR_DAY_S) * 10 * 60 % 360)
        orbit_geometry.ground_track(0,16).lon.should be_close_to -((360.0/Orbits::SOLAR_DAY_S) * 16 * 60 % 360)
        orbit_geometry.ground_track(0,22.5).lon.should be_close_to -5.625
        orbit_geometry.ground_track(0,23).lon.should be_close_to 180 - ((360.0/Orbits::SOLAR_DAY_S) * 23 * 60 % 360)
        orbit_geometry.ground_track(0,30).lon.should be_close_to 180 - ((360.0/Orbits::SOLAR_DAY_S) * 30 * 60 % 360)
        orbit_geometry.ground_track(0,67.5).lon.should be_close_to -16.875
      end
    end
  end 

  context "#along_track_swath_edges", :focus => true do
    describe "ground track swath edges returns correct values" do
      it "returns correct swath edges for retrograde orbit" do
        orbit_geometry = Orbits::OrbitGeometry.new(Orbits::Orbit.new(120, 100, 1450.0, -90, 0.5))
        assert_equal_kml(orbit_geometry, 50, 10, 240,"retrograde.kml")
      end

      it "returns correct swath edges for prograde orbit" do
        orbit_geometry = Orbits::OrbitGeometry.new(Orbits::Orbit.new(75, 100, 1450.0, -90, 0.5))
        assert_equal_kml(orbit_geometry, -50, 10, 240,"prograde.kml")
      end

      it "returns correct swath edges for polar orbit" do
        orbit_geometry = Orbits::OrbitGeometry.new(Orbits::Orbit.new(90, 100, 1450.0, -90, 0.5))
        assert_equal_kml(orbit_geometry, -150, 10, 120,"polar.kml")
      end

      it "returns correct swath edges for equitorial orbit" do
        orbit_geometry = Orbits::OrbitGeometry.new(Orbits::Orbit.new(0, 100, 1450.0, -90, 0.5))
        assert_equal_kml(orbit_geometry, 150, 0, 20,"equitorial.kml")
      end
    end


    def assert_equal_kml(orbit_geometry, longitude_crossing, start_time, end_time,file_name)
      actual_kml = generate_kml(file_name, start_time,end_time) do |minute| 
        edge = orbit_geometry.along_track_swath_edges(longitude_crossing, minute)
        "<LineString><tessellate>1</tessellate><coordinates>#{edge[0].lon},#{edge[0].lat} #{edge[1].lon},#{edge[1].lat}</coordinates></LineString>\n"
      end
      # File.open(File.dirname(__FILE__) + "/test_data/#{file_name}", 'w') { |file| file.write(actual_kml) }
      expected_kml = File.open(File.dirname(__FILE__) + "/test_data/#{file_name}", 'r').read
      expected_kml.should be == actual_kml
    end

    def generate_kml(name, start_time, end_time, &block)
    	swath_lines = ""
    	(start_time..end_time).each {|minute| swath_lines += yield(minute)}
    	kml =<<stop
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2" xmlns:kml="http://www.opengis.net/kml/2.2" xmlns:atom="http://www.w3.org/2005/Atom">
<Document>
	<name></name>
	<Placemark>
		<name>#{name}</name>
		<MultiGeometry>
		#{swath_lines}
		</MultiGeometry>
	</Placemark>
</Document>
</kml>
stop
	    kml
    end

  end
end