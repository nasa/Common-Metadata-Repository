module Mock
  class BoundingRectangle
    attr_accessor :south, :north, :west, :east
    def initialize(south, north, west, east)
      @south, @north, @west, @east = south, north, west, east
    end
  end

  class Point
    attr_accessor :lat, :lon
    def initialize(lat, lon)
      @lat, @lon = lat, lon
    end
  end

  class Ring
    attr_accessor :points

    def initialize(points)
      @points = points
    end
  end

  class Line
    attr_accessor :points
    def initialize(lat_lons)
      @points = lat_lons.map {|lat, lon| Point.new(lat, lon)}
    end
  end

  class Polygon
    attr_accessor :rings

    def initialize(lat_lons)
      points = lat_lons.map {|lat, lon| Point.new(lat, lon)}
      points << points.first
      @rings = [Ring.new(points)]
    end
  end
end


