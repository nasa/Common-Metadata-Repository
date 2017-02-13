module Orbits
  class Coordinate
    include Math

    def self.lat_lon(lat, lon)
      phi = lat * PI / 180
      theta = lon * PI / 180
      phi_theta(phi, theta)
    end

    def self.xyz(x, y, z)
      d = x*x + y*y + z*z
      d = x = 1 if d == 0 # Should never happen, but stay safe

      # We normalize so that x, y, and z fall on a unit sphere
      scale = 1 / Math.sqrt(d)
      new.instance_eval do
        @x = x * scale
        @y = y * scale
        @z = z * scale
        self
      end
    end

    def self.phi_theta(phi, theta)
      # Normalize phi to the interval [-PI / 2, PI / 2]
      phi -= 2 * PI while phi >= PI
      phi += 2 * PI while phi < -PI
      if phi > PI / 2
        phi = PI - phi
        theta += PI
      end
      if phi < -PI / 2
        phi = -PI - phi
        theta += PI
      end

      # Normalize theta to the interval [-PI, PI)
      theta -= 2 * PI while theta >= PI
      theta += 2 * PI while theta < -PI

      new.instance_eval do
        @phi = phi
        @theta = theta
        self
      end
    end

    def initialize
      @phi, @theta, @lat, @lon, @x, @y, @z = nil
    end

    def lat
      @lat ||= phi * 180 / PI
    end

    def lon
      @lon ||= theta * 180 / PI
    end

    def phi
      unless @phi
        if @z
          @phi ||= asin(z)
        elsif @lat
          @phi ||= lat * PI / 180
        else
          raise "Could not convert coordinate."
        end
      end
      @phi
    end

    def theta
      unless @theta
        if @z
          @theta ||= atan2(y, x)
        elsif @lon
          @theta ||= lon * PI / 180
        else
          raise "Could not convert coordinate."
        end
      end
      @theta
    end

    def x
      @x ||= cos(phi) * cos(theta)
    end

    def y
      @y ||= cos(phi) * sin(theta)
    end

    def z
      @z ||= sin(phi)
    end

    def dot(other)
      x * other.x + y * other.y + z * other.z
    end

    def *(other)
      # Normalized cross product
      Coordinate.xyz(y * other.z - z * other.y,
                     z * other.x - x * other.z,
                     x * other.y - y * other.x)
    end

    def -@
      Coordinate.xyz(-x, -y, -z)
    end

    def to_s
      "(%7.3f, %8.3f)" % [lat, lon]
    end

    def to_xyz_s
      "<%.3f, %.3f, %.3f>" % [x, y, z]
    end
  end
end
