# This is a Ruby implementation of the Backtrack Orbit Search Algorithm (BOSA)
# originally written in Java and maintained by [NSIDC](http://nsidc.org/).
# References to "the Java code" or "the NSIDC spheres code" in the refer
# to the original library which is available
# [here](http://geospatialmethods.org/spheres/).  Along with the original
# code, geospatialmethods.org also has a [description of the algorithm]
# (http://geospatialmethods.org/bosa/).
#
#### Overview
#
# Orbiting satellites have sensors that trace a circular path around the
# Earth.  It is difficult to express this ground track as a 2D polygon
# with lat/lon coordinates.  Most of the orbits we deal with have a
# long, narrow, sinusoidal shape that would require hundreds of points
# to describe.  Further, their minimum bounding rectangle covers nearly
# the whole Earth and some orbits have ground tracks that cross themselves
# within a single granule of data.
#
# Instead of describing the orbit as a complex 2D polygon, we want to
# answer a different question:
#
# *Where would the orbit need to cross the equator in order to see
# a given point on the Earth?*
#
# Instead of using a complicated 2D polygon query, we could instead perform
# a simple range query: "Find all data granules whose orbit crosses the
# equator between longitudes `a` and `b`."
#
# This class contains the methods used to determine the equator crossing range
# for a given orbit and point on the Earth.
#
# If we want to find data which intersects a more complicated area of the
# Earth, we can decompose that area into points and perform a similar range
# query.  We add points along the perimiter of the shape so that any orbit
# that intersects the shape will pass over points on the perimiter.  This
# process of adding points is called *densification*.
#
#### Conventions
#
# Where readability allows, we suffix variables with their units
#
#   * _rad = radians (Earth radii)
#   * _deg = degrees
#   * _m = meters
#   * _s = seconds
#
# We use some default units where no suffix is present:
#
#   * latitude and longitude: degrees
#   * other angles and distances: radians (Earth radii)
#   * time: seconds
#
# phi (`phi`) and theta (`theta`) are used to denote the radian counterparts
# of latitude and longitude respectively, since the latter are almost always
# expressed in degrees.
#
#### Definitions
#
#   * **inclination**: Angle of the orbit from the equator.  Numbers greater
#       than 90 degrees are common and indicate a retrograde orbit
#   * **prograde orbit**: An orbit that travels in the direction of the
#       Earth's rotation
#   * **retrograde orbit**: An orbit that travels in the direction opposite
#       the Earth's rotation
#   * **inflection point**: A point where the orbit reaches its highest
#       or lowest latitude
#   * **declination**: The angle between the orbits inflection points and the
#       nearest pole.  Note that this definition is similar to magnetic
#       compass declination.  Celestial declination is a different concept.
#   * **nadir**: The opposite of zenith - a vector pointing straight down at a
#       given point.

#
require 'orbits/longitude_coverage'
require 'orbits/coordinate'
require 'orbits/geometry_to_coordinates_converter'
require 'orbits/geometry_backtracking'

module Orbits
  ### Orbit class definition
  class Orbit
    include Math
    include GeometryBacktracking

    #### Attributes

    # The inclination angle of the orbit in radians
    attr_reader :inclination_rad

    # The number of seconds it takes to complete one orbit
    attr_reader :period_s

    # The width of the orbital track observed by the sensor, in Earth radians.
    # This width is perpendicular to the orbit's track, and runs along the
    # Earth's surface.  It is measured at the equator, which only matters if
    # we use an ellipsoidal Earth at some point in the future.
    attr_reader :swath_width_rad

    # The starting circular latitude of each orbital pass.
    attr_reader :start_clat_rad

    # The number of orbits per granule of data (may be a fraction)
    attr_reader :count

    #### Constructor
    # We accept the parameters and units stored in ECHO but convert them to
    # units that are more useful internally.
    #
    # * **inclination_deg** - The number of degrees of inclination for the orbit
    # * **period_min** - The number of minutes it takes to complete one orbit
    # * **swath_width_km** - The width of the orbital track in kilometers
    # * **start_clat_deg** - The starting circular latitude in degrees
    # * **count** - The number of orbits per granule of data (may be a fraction)
    def initialize(inclination_deg, period_min, swath_width_km, start_clat_deg, count=0)
      @declination_rad = nil # lazy
      @inclination_rad = inclination_deg * PI / 180.0
      @period_s = period_min * 60
      @swath_width_rad = swath_width_km * 1000 / EARTH_RADIUS_M
      @start_clat_rad = start_clat_deg * PI / 180.0
      @count = count
    end

    #### Accessors / Conversions

    ##### declination_rad
    # This declination is the angle between the top-most point of the orbit
    # and the north pole, similar to magnetic compass declination.  This
    # should not be confused with celestial declination
    def declination_rad
      if @declination_rad.nil?
        @declination_rad = inclination_rad - PI / 2
        @declination_rad -= PI / 2 while @declination_rad > PI / 2
      end
      @declination_rad
    end

    ##### retrograde?
    # True if the orbit is a retrograde orbit (moves in the opposite direction
    # of the Earth's rotation), false if it is prograde (moves in the same direction
    # of the Earth's rotation).  Note that all of the orbits in ECHO are retrograde,
    # so retrograde cases are more thoroughly tested.  The Java code seems to
    # assume that orbits are retrograde.
    def retrograde?
      inclination_rad > PI / 2
    end

    ##### max_orbit_phi
    # The maximum `phi` (latitude) the orbit is able to reach
    def max_orbit_phi
      PI / 2 - declination_rad
    end

    ##### min_orbit_phi
    # The minimum `phi` (latitude) the orbit is able to reach
    def min_orbit_phi
      -max_orbit_phi
    end

    ##### angular_velocity_rad_s
    # The angular velocity of the orbit, in radians per second.  In other words,
    # the number of radians the orbiting satellite covers per second.
    def angular_velocity_rad_s
      2 * PI / period_s
    end

    ##### full_coverage_phi
    # Some orbits have swaths that cross the poles.  Every pass for these orbits
    # will observe the pole and surrounding points down to a certain latitude.
    # This method returns lowest positive `phi` (latitude) that every orbital
    # pass can see.  If it's larger than `pi/2`, then the orbital swaths don't
    # pass over the poles.
    def full_coverage_phi
      PI - max_coverage_phi
    end

    ##### max_coverage_phi
    # The maximum `phi` (latitude) that the orbit can see.  The orbital swath
    # can never observe points above this `phi` value.
    #
    # See: full_coverage_phi
    def max_coverage_phi
      max_orbit_phi + swath_width_rad / 2
    end

    ##### to_s
    # The format of to_s appears strange, but its the same representation
    # used by the Java code, so it helps in debugging.
    def to_s
      inflection_diffs = [-swath_width_rad / 2, 0, swath_width_rad / 2]
      inflections = inflection_diffs.map {|d| ((max_orbit_phi + d) * 180 / PI).round(3)}

      "Orbit with inflections #{inflections.inspect}"
    end

    #### Core backtracking methods

    # This is slow and has accuracy problems.  Do not use it for production.
    # It is here for reference and testing / validation.
    # Use area_crossing_range (defined in GeometryBacktracking) instead
    def densified_area_crossing_range(geometry, ascending)
      separation = swath_width_rad * 0.9
      range = LongitudeCoverage.none
      GeometryToCoordinatesConverter.coordinates(geometry, separation).each do |coord|
        range << coord_crossing_range(coord, ascending)
      end
      range
    end

    # Use the faster area crossing range defined in GeometryBacktracking
    alias_method :area_crossing_range, :fast_area_crossing_range

    # Uncomment to cause really bad performance by swapping implementations
    # of area_crossing_range
    #alias_method :area_crossing_range, :densified_area_crossing_range

    ##### coord_crossing_range
    #
    # This method performs the Backtrack Orbit Search Algorithm.
    #
    # Given a coordinate, returns a range of longitudes.  Orbital passes
    # which cross the equator within the returned range will cover the
    # given coordinate with their swath.
    #
    # Parameters:
    # * **coord** - The coordinate whose equator crossing range we're
    #   interested in (Orbit::Coordinate)
    # * **ascending** - A boolean value.  If true, we return ranges
    #   where the orbit crosses the equator on its ascending pass.  If
    #   false, we return ranges where the orbit crosses the equator on
    #   its descending pass.
    #
    # Returns an Orbit::LongitudeCoverage
    def coord_crossing_range(coord, ascending)
      # If the point is above the max coverage `phi`, there's no coverage
      return LongitudeCoverage.none if coord.phi.abs > max_coverage_phi

      # If the point is above the full coverage `phi`, it's covered by every pass
      return LongitudeCoverage.full if coord.phi.abs > full_coverage_phi

      # We start by figuring out how wide the swath is at the coordinate,
      # along the longitude lines.  This is different from the orbit's
      # swath width parameter, because the measurement runs parallel to
      # the equator, not perpendicular to the orbit.
      west_edge, east_edge = *horizontal_swath_edges(coord, ascending)

      # If the swath has no edges at the coordinate, there's no coverage
      return LongitudeCoverage.none if west_edge.nil? || east_edge.nil?

      # TODO one edge may pass over the equator before the other, and the
      #      Earth will rotate in the mean time, which will skew the
      #      longitude range slightly.  The NSIDC site mentions this, but
      #      the Java code doesn't account for it.

      # This part is a little tricky.  The swath area to the west of the point
      # is covered by a range of orbits to the east of the point, so we use
      # the west swath edge to calculate the range of orbits crossing to the
      # east and vice versa.
      west_width = west_edge.theta - coord.theta
      east_target = Coordinate.phi_theta(coord.phi, coord.theta - west_width)

      east_width = east_edge.theta - coord.theta
      west_target = Coordinate.phi_theta(coord.phi, coord.theta - east_width)

      # Figure out where the orbits through each of our targets cross the
      # equator, compensating for the Earth's rotation (the boolean true value).
      min_crossing = equator_crossing(west_target, ascending, true).theta
      max_crossing = equator_crossing(east_target, ascending, true).theta

      # Construct and return a coverage object
      LongitudeCoverage.new(min_crossing, max_crossing)
    end

    private

    ##### horizontal_swath_edges
    #
    # Given coord, a coordinate within the orbital swath, returns an array
    # of 2 points with the same latitude as coord which lie on the swath edges.
    # The first point lies on the edge to the west of coord, the second to the
    # east.
    #
    # If the coordinate cannot fall within the swath, returns an empty array
    #
    # Parameters:
    # * **coord** - A coordinate (Orbit::Coordinate) within the swath
    # * **ascending** - A boolean value.  If true, we return edges for the
    #   orbit's ascending pass.  If false, we return edges for the descending
    #   pass.
    #
    # Returns an array containing 0 or 2 Orbit::Coordinate instances
    def horizontal_swath_edges(coord, ascending)
      # Candidate solutions.  We may end up finding more points than we need.
      # Before returning, we'll take the two points closest to the given
      # coordinate
      candidates = []

      # We need to be able to assume the coordinate isn't on the equator, so
      # we'll return early if we find that it is.  We know the swath width
      # from the orbit parameters.  This calculation is slightly off, since
      # the swath width is measured perpendicular to the orbit, but the
      # Java code makes this assumption.  This case should almost never
      # happen.  When it does, the numbers are likely close enough.

      if coord.phi.abs <= EPSILON
        return [Coordinate.phi_theta(0, coord.theta - swath_width_rad / 2),
                Coordinate.phi_theta(0, coord.theta + swath_width_rad / 2)]
      end

      # We ignore the rotation of the Earth for the purposes of this method.
      # The values we return (the swath edges) are not influenced by the
      # Earth's rotation.  Intermediate values are, but they're not visible
      # outside of this method.
      #
      # Ignoring the rotation of the Earth, we can imagine three circles
      # traced by the orbit.  The first is the path on the ground directly
      # beneath the orbit.  This is a great circle whose center is the origin.
      # The other two are the paths traced by the swath edges, which are not
      # great circles and not centered at the origin.
      #
      # These three circles each lie in a plane, and the three planes are
      # parallel to each other.
      #
      # There's another circle we're interested in, which is the circle
      # traced by the latitude line at coord.  This circle lies in its own
      # plane which is not parallel to the other three.
      #
      # The four original circles are the intersections of their respective
      # planes and the sphere of the Earth.
      #
      # So, to find the swath's edge coordinates, we need to find the
      # intersection between three things:
      #
      #   1. `x^2 + y^2 + z^2 = 1`
      #
      #      The sphere of the Earth.  We've scaled everything so the Earth
      #      has radius 1, and it's centered on the origin.  Convenient.
      #
      #   2. `ax + by + cz = d`
      #
      #      The swath's plane.  `a`, `b`, `c` and `d` are constants which
      #      we'll calculate.  Since they're parallel, the swath edges share
      #      `a`, `b`, and `c` with the orbital plane, but each edge has a
      #      different `d` value.
      #
      #   3. `z = "coord.z"`
      #
      #      The plane slicing through latitude = coord.lat

      # We already know z

      z = coord.z

      # We'll find `a`, `b`, and `c` for the orbit's plane, which are the same
      # constants for the swath's plane.  To find them, we need 3 non-colinear
      # points in the orbit's plane.  The origin is one, but we need two more.

      # First, we'll get a point that the orbit can pass over which is close
      # to coord.  Usually we'll just use coord, but if coord's latitude is
      # above the orbit's inflection point, we need to find a point with a
      # lower latitude

      target_phi = coord.phi
      target_phi = max_orbit_phi if target_phi > max_orbit_phi
      target_phi = min_orbit_phi if target_phi < min_orbit_phi
      target = Coordinate.phi_theta(target_phi, coord.theta)

      # For the second point, we'll use the equatorial crossing of the orbit
      # through the target.  This is safe, because we bailed earlier if
      # the target was on the equator.  The "false" parameter tells the method
      # to ignore the Earth's rotation.

      cross = equator_crossing(target, ascending, false)

      # For a plane passing through the origin, `ax + by + cz = 0`.  With our
      # two other points, we can solve for `a`, `b`, and `c`:

      a = target.y * cross.z - target.z * cross.y
      b = target.z * cross.x - target.x * cross.z
      c = target.x * cross.y - target.y * cross.x

      # If we can find a point on the swath edge, we can calculate `d`.  We
      # can find a point on each edge by finding the orbit's inflection point
      # and adding/subtracting half the swath width from its latitude.

      inf = Coordinate.phi_theta(-inclination_rad, cross.theta - PI / 2)

      # Calculate solutions for the point p on each swath edge

      [Coordinate.phi_theta(inf.phi + swath_width_rad / 2, inf.theta),
       Coordinate.phi_theta(inf.phi - swath_width_rad / 2, inf.theta)].each do |p|

        # Calculate `d` from the plane equation

        d = a * p.x + b * p.y + c * p.z

        # The fact that `z` is constant is very handy.  It means we really only
        # care about two dimensions.  We're really trying to find the intersection
        # of a line (the edge's plane when `z = "coord.z"`) and a circle (the part
        # of the Earth intersecting `z = "coord.z"`).
        #
        # Note: that simplified math could make it easier to switch this
        # algorithm to use an ellipsoidal Earth (WGS84) in the future, since we
        # would just be intersecting a line with a slightly different circle.
        #
        # The swath plane's equation is:
        #
        # `ax + by + cz = d`
        #
        # `a`, `b`, `c`, `d`, and `z` are all known at this point, so we just
        # need to figure out `x` and `y`.  Solve the equation for `y`
        #
        # `y = -a/b x + (d - cz) / b`
        #
        # Which is the line we need to intersect with the latitude circle.

        # We can't just compute constants for the line, because `b` could be near 0.
        # Let's deal with that special case first.

        if b.abs < 0.000001

          # If `b = 0`, then the plane's equation is:
          #
          # `ax + 0y + cz = d`
          #
          # Solving for `x`:
          #
          # `x = (d - cz) / a`

          # Ok, another problem.  It's possible that both `a` and `b` are 0.  In that
          # case, we have an orbit that follows the equator.  If coord lies within the
          # swath, then every orbit will cover the point, otherwise no orbits will.

          if a.abs < 0.000001
            if coord.phi.abs < swath_width_rad / 2
              return [Coordinate.phi_theta(0, -PI), Coordinate.phi_theta(0, PI)]
            else
              return []
            end
          end

          # Now we can handle the case where `b` is 0 and `a` isn't

          x = (d - c * z) / a

          # We now know `x` and `z`, so we can use the sphere equation to solve for `y`.
          #
          # `y = +- sqrt(1 - z^2 - x^2)`
          #
          # In this case, `1 - z^2 - x^2` could be negative.  This would indicate
          # that the latitude does not intersect the swath edge.  Perhaps the point is
          # completely above one swath edge but below the other.  We won't worry
          # about duplicate solutions (`y = +- 0`), since the LongitudeCoverage
          # class will combine them correctly.

          dis = 1 - z**2 - x**2

          if dis >= 0
            y = sqrt(dis)
            candidates << Coordinate.xyz(x, y, z)
            candidates << Coordinate.xyz(x, -y, z)
          end

        # Now the case when b is not 0.
        else
          # Recall, the line's equation is:
          #
          # `y = -a/b x + (d - cz) / b`
          #
          # We'll call those constants `m` and `k`

          m = -a / b
          k = (d - c * z) / b

          # Which means
          #
          # `y = mx + k`
          #
          # (We won't use the standard `b` as the `y`-intercept name, since
          # it means something different in the plane equation).

          # We need to find the intersection of that line with the latitude
          # circle at `z = "coord.z"`.  Using the sphere equation with a
          # constant z, we get the latitude circle's equation:
          #
          #  `x^2 + y^2 = 1 - z^2`

          # It's centered at the origin.  `1 - z^2` is the radius squared.
          #
          # Taking the square root is safe because we calculated z from a
          # lat/lon coordinate.  Further, a negative radius doesn't make sense.

          r = sqrt(1 - z**2)

          # So,
          #
          # `x^2 + y^2 = r^2`
          #
          # Substituting `y` with the value from our line equation:
          #
          # `x^2 + (mx + k)^2 = r^2`
          #
          # Multiplying out, we get:
          #
          # `x^2 + m^2x^2 + 2mkx + k^2 = r^2`

          # Grouping constants, we get:
          #
          # `(m^2 + 1)x^2 + (2mk)x + (k^2 - r^2) = 0`
          #
          # We call those constants `a_q`, `b_q`, and `c_q`

          a_q = m**2 + 1
          b_q = 2 * m * k
          c_q = k**2 - r**2

          # So:
          #
          # `a_q x^2 + b_qx + c_q = 0`

          # We can solve this using the quadratic equation:
          #
          # `x = (-b +- sqrt(b^2 - 4ac)) / (2a)`

          discriminant = b_q**2 - 4 * a_q * c_q

          # The discriminant could be negative if the line and circle do
          # not intersect
          if discriminant >= 0
            # There are two possible `x` values
            [(-b_q + sqrt(discriminant)) / (2*a_q),
             (-b_q - sqrt(discriminant)) / (2*a_q)].each do |x2|
              # Now we need `y`
              y = m * x2 + k

              candidates << Coordinate.xyz(x2, y, z)
            end
          end
        end
      end

      # Return if we haven't found any valid points
      return [] if candidates.empty?

      # At this point, we have either 2 or 4 candidate points that are swath
      # edge intersections with the target latitude.  Now we need to find
      # the two that are closest to the coordinate the caller passed.  We
      # want them in west-to-east order to make things simpler for the caller.

      # Find the index of the first candidate to the east of the target
      candidates.sort_by!(&:theta)
      east_index = candidates.find_index { |c2| coord.theta < c2.theta }

      # Handle wrapping around the date line
      east_index = 0 if east_index.nil?

      # The west index is the one to the left of the east index, wrapping around
      # the date line if needed
      west_index = (east_index - 1) % candidates.size

      west_edge = candidates[west_index]
      east_edge = candidates[east_index]

      # There is a special case here.  If we only found two candidates, that
      # indicates that the point is above one swath edge, but below the other,
      # which happens at latitudes near the inflection point.
      #
      # This method returns swath edges for either the ascending or descending
      # pass, not both.  If both of our candidates are on the same edge, then
      # one is seen on the ascending pass and the other on the descending. We
      # solve this problem by moving one edge point to the inflection longitude.
      #
      # Note: the rest of this method will produce the same results regardless
      #       of the value of "ascending."  Factoring this out could slightly
      #       improve performance.

      xprod = target * cross
      north_hemisphere = coord.phi > 0
      if candidates.size == 2
        inf_theta = north_hemisphere ? inf.theta + PI : inf.theta
        inflection = Coordinate.phi_theta(coord.phi, inf_theta)
        if north_hemisphere && retrograde? == ascending ||
            !north_hemisphere && retrograde? != ascending
          west_edge = inflection
        else
          east_edge = inflection
        end
      end

      # Return the edges
      [west_edge, east_edge]
    end

    ##### equator_crossing
    #
    # Find the points at which the orbit passing through coord
    # crosses the equator on its ascending pass (if ascending
    # is true) or its descending pass (if ascending is false).
    # If corrected is true, we will correct for the rotation of
    # the Earth, otherwise we won't.
    def equator_crossing(coord, ascending, corrected=true)
      # [This page](http://mathworld.wolfram.com/SphericalTrigonometry.html)
      # has a good diagram and a summary of the math we'll need to use.
      #
      # [This image](http://geospatialmethods.org/bosa/triangles.gif)
      # is also helpful.  (`Lat_p`, `Lon_p`) is what we call `C`.
      # `(0, Lon_p)` is what we call `B`.  `(0, Lon_n)` is
      # what we call `A`.
      #
      # In order to figure out the crossing point, we need to
      # set up a spherical triangle formed by:
      #
      # point `C` - the given coordinate
      # point `B` - the point at (0, coord.theta)
      # point `A` - the (unknown) point where the orbit crosses the equator
      #
      # To remain consistent with the Wolfram and similar
      # references, we'll use A, B, and C notation.  "`A`" will
      # denote the angle at point `A`.  "`a`" will denote the arc-length
      # of the side across from point `A`.  Our capital variable names will
      # use underscores to remain valid in Ruby, e.g. "_A_"
      #
      # We know two angles:

      # Because side `A` is on a latitude line and side `c` is on
      # the equator

      _B_ = PI / 2

      # `A` is an angle between the orbit and the equator, which
      # is the inclination if it's a retrograde orbit in the
      # descending pass or a prograde orbit in the ascending
      # pass.  Otherwise it's the complement of the inclination

      if retrograde? != ascending
        _A_ = inclination_rad
      else
        _A_ = PI - inclination_rad
      end

      # We also know one side's arc length:

      a = coord.phi

      # Coord could lie above or below the orbit.  We find the nearest
      # inflection point in that case.
      a = max_orbit_phi if a > max_orbit_phi
      a = min_orbit_phi if a < min_orbit_phi

      # We need to find:
      #
      # 1. `c`, which we can add to coord's longitude to find
      #     the crossing longitude
      # 2. `b`, which is the arc length traveled since crossing the
      #     equator.  We can use this to calculate the amount
      #     the Earth has rotated since the satellite crossed
      #     the equator and correct accordingly.

      # Since we know `A`, `a`, and `B`, we can use the law of sines
      # to calculate `b`
      #
      # `sin(A) / sin(a) = sin(B) / sin(b) = sin(\C) / sin(c)`

      sin_b = sin(_B_) * sin(a) / sin(_A_)
      b = asin(sin_b)

      # Finding `c` is trickier because we don't know `C`.  We use [Napier's
      # first analogy](http://mathworld.wolfram.com/NapiersAnalogies.html):
      #
      # `sin((A - B)/2) / sin((A + B)/2) = tan((a - b)/2) / tan(c/2)`
      #
      # Rearranging:
      #
      # `tan(c/2) = (tan((a - b)/2) * sin((A + B)/2)) / sin((A - B)/2)`
      #
      # Since `sin((A - B)/2)` could be 0, we'll need to use atan2 and
      # keep the numerator and denominator separate

      numerator = tan((a - b) / 2) * sin(( _A_ + _B_ ) / 2)
      denominator = sin((_A_ - _B_) / 2)

      c = 2 * atan2(numerator, denominator)

      # We now know where the orbit crosses
      crossing_theta = coord.theta + c

      # For the descending pass, we ended up calculating the point half
      # way around the globe, so we need to correct
      crossing_theta -= PI unless ascending

      # Correct for the rotation of the Earth, if necessary
      if corrected
        # Recall, `b` is the angle of the orbit travelled since
        # crossing the equator on the ascending pass (in radians).
        # We can use this to calculate the angular distance covered
        # by the orbit on the requested pass.

        if ascending
          distance_rad = b
        else
          distance_rad = PI - b
        end

        # If the distance is below the starting latitude, the orbit will
        # need to make an additional pass before reaching the target point,
        # so we add `2pi` to the distance.
        distance_rad += 2 * PI while distance_rad < start_clat_rad
        distance_rad -= 2 * PI while distance_rad > start_clat_rad + 2 * PI

        # `"time" = "distance" / "rate"`
        #
        # We know distance from above and rate is given by the orbit parameters,
        # so we can calculate the time since crossing the equator.

        time_since_crossing_s = distance_rad / angular_velocity_rad_s

        # Now we want to know how far the Earth has rotated since we crossed
        # the equator.  We know the Earth's rotation rate and the elapsed time

        earth_rotation_rad = EARTH_ANGULAR_VELOCITY_RAD_S * time_since_crossing_s

        # Since we're working with a unit sphere, we can make the
        # correction directly

        crossing_theta += earth_rotation_rad
      end

      # Return the coordinate
      Coordinate.phi_theta(0, crossing_theta)
    end
  end
end
