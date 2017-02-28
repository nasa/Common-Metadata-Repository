require 'orbits/coordinate'
require 'orbits/transformation'

module Orbits
  module GeometryToCoordinatesConverter
    def self.coordinates(geometry, minimum_separation_rad, &block)
      # I used respond_to? instead of checking class names to avoid an
      # explicit dependency on GeoRuby and allow easier mocking/testing
      if geometry.respond_to? :lat
        densify_point(geometry, minimum_separation_rad).each(&block)
      elsif geometry.respond_to? :rings
        densify_polygon(geometry, minimum_separation_rad).each(&block)
      elsif geometry.respond_to? :points
        densify_line(geometry, minimum_separation_rad).each(&block)
      elsif geometry.respond_to? :west
        densify_bounding_rectangle(geometry, minimum_separation_rad).each(&block)
      else
        raise "Unrecognized geometry: #{geometry.inspect}"
      end
    end

    private

    def self.densify_point(point, angle=0)
      [Coordinate.lat_lon(point.lat, point.lon)]
    end

    def self.densify_polygon(polygon, angle)
      Enumerator.new do |y|
        coords = polygon.rings.first.points.map {|p| Coordinate.lat_lon(p.lat, p.lon)}
        # pair adjacent points
        segments = coords.slice(0..-2).zip(coords.slice(1..-1))
        densify_segments(segments, angle,y)
      end
    end

    def self.densify_line(line, angle)
      Enumerator.new do |y|
        coords = line.points.map {|p| Coordinate.lat_lon(p.lat, p.lon)}
        # pair adjacent points
        segments = coords.slice(0..-2).zip(coords.slice(1..-1))
        densify_segments(segments, angle, y)

      end
    end

    def self.densify_bounding_rectangle(rect, angle)
      Enumerator.new do |y|
        north, south, east, west = rect.north, rect.south, rect.east, rect.west

        east += 360 if east < west
        separation = angle * 180 / Math::PI

        each_angle(west, east, separation) {|lon| y.yield Coordinate.lat_lon(north, lon)}
        each_angle(north, south, separation) {|lat| y.yield Coordinate.lat_lon(lat, east)}
        each_angle(west, east, separation) {|lon| y.yield Coordinate.lat_lon(0,lon)} if north > 0 && south < 0
        each_angle(east, west, separation) {|lon| y.yield Coordinate.lat_lon(south, lon)}
        each_angle(south, north, separation) {|lat| y.yield Coordinate.lat_lon(lat, west)}
      end
    end

    def self.each_angle(a, b, step)
      step = -step if a < b && step < 0 || a > b && step > 0
      value = a
      while a < b && value < b || a > b && value > b
        yield value
        value += step
      end
    end

    private

    def self.densify_segments(segments, angle, y)
      segments.each do |p, q|
        p_dot_q = p.dot(q)
        transformation = Transformation.great_circle_rotation(p, q, angle)
        coord = p

        # The dot product of two unit vectors is the cosine of the angle between
        # them.  The larger the angle, the smaller the dot product.  When p dot q
        # is larger than p dot coord, coord has gone past q
        while p.dot(coord) - p_dot_q > 0.000001
          y.yield coord
          coord = transformation.apply coord
        end
      end
    end
  end
end
