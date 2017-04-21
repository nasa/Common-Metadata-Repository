# Unset gem related environment variables to prevent jruby from loading gems from client's GEM_PATH
ENV.delete('GEM_HOME')
ENV.delete('GEM_PATH')

require 'rubygems'
require 'orbits/circular_range'
require 'orbits'

# structure classes to allow passing arguments to echo-orbits library methods
class Point
	attr_reader :lat, :lon
	def initialize(coordinates)
		@lon = coordinates[0]
		@lat = coordinates[1]
	end
end

class Line
	attr_reader :points
	def initialize(coordinates)
		@points = []
		coordinates.each_slice(2) {|a| @points << Point.new(a)}
	end
end

class MBR
	attr_reader :west, :north, :east, :south
	def initialize(coordinates)
		@west = coordinates[0]
		@north = coordinates[1]
		@east = coordinates[2]
		@south = coordinates[3]
	end
end


class Ring
	attr_reader :points
	def initialize(coordinates)
		@points = []
		coordinates.each_slice(2) {|a| @points << Point.new(a)}
	end
end

# a single ring polygon
class Polygon
	attr_reader :rings
	def initialize(coordinates)
		@rings = [Ring.new(coordinates)]
	end
end


include Math
include Spatial

EARTH_ANGULAR_VELOCITY_RAD_SEC = 7.2921150 * 10**-5
EARTH_ANGULAR_VELOCITY_DEG_SEC = EARTH_ANGULAR_VELOCITY_RAD_SEC * 180.0 / Math::PI

def denormalizeLatitudeRange(min, max)
	cr = CircularRange.new(min, max).denormalize(0,720).flatten
	asc_ranges = cr.map do |range|
		[range.begin, range.end]
	end

	cr = CircularRange.new(180 - max, 180 - min).denormalize(0,720).flatten
	desc_ranges = cr.map do |range|
		[range.begin, range.end]
	end

	[asc_ranges, desc_ranges]
end

def areaCrossingRange(lat_range, type, coordinates, ascending, inclination_deg, period_min,
	swath_width_km, start_clat_deg, count=0)
	geometry = nil
	case type
	when "br"
		geometry = MBR.new(coordinates)
	when "line"
		geometry = Line.new(coordinates)
	when "point"
		geometry = Point.new(coordinates)
	else # polygon
		geometry = Polygon.new(coordinates)
	end
	orbit = Orbits::Orbit.new(inclination_deg, period_min, swath_width_km, start_clat_deg, count)
	lat_and_lon_ranges = orbit.area_crossing_range(lat_range, geometry, ascending)
	rval = []

	lat_and_lon_ranges.each do |lat_range, lon_ranges|
		lon_ranges = lon_ranges.to_a
		shifted_ranges = []
		(0...count).each do |i|
			shifted_ranges += shift_ranges(lon_ranges, orbit.period_s * EARTH_ANGULAR_VELOCITY_DEG_SEC * i)
			end
			shifted_ranges.flatten!
			shifted_ranges = shifted_ranges.map do |range|
				[range.begin, range.end]
			end
			rval << [[lat_range], shifted_ranges]
		end
		rval
end

# Shifts the given list of ranges by amount degrees
def shift_ranges(ranges, amount)
  ranges.map do |range|
    CircularRange.new(range[0] + amount, range[1] + amount).denormalize(-180,180)
  end
end
