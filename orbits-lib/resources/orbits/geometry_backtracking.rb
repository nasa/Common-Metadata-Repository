# Faster implementation of area_crossing_range for Orbits::Orbit
#
# Instead of "densifying" points using GeometryToCoordinatesConverter
# and backtracking each point individuall, this algorithm deals with
# pairs of points.  It identifies segments which will have a continuous
# range of backtracked longitudes, backtracks the endpoints of the
# segments, and produces a continous ranged between the backtracking
# results.
#
# With the old algorithm, finding the area crossing for a bounding box
# edge spanning 180 degrees and a 2km swath width orbit required
# about 10,000 iterations of the backtracking algorithm, taking multiple
# seconds.  The new algorithm finds the area crossing for such a
# segment with 2 backtracking iterations and finishes in under a millisecond.
#
# Note: This module is very tightly coupled with Orbits::Orbit.  It
# is not meant to exist separately.  It is here to avoid detracting from the
# core backtracking algorithm's readability.  This code is much uglier and
# has to deal with more edge cases (heh, edge cases).
module Orbits
  module GeometryBacktracking
    include Math

    EPSILON = 0.00001 # Small number, slightly larger than expected numeric errors

    # Replaces the previous area_crossing_range.  debug=true is used to return
    # intermediate results to debug visualizations.  It should not be set for
    # any production purposes.
    # This function returns results as an array consisting of one or more pairs of
    # latitude range / LongitudeCoverage objects. The latitude range is an array of two elements
    # consisting of the lower and the upper latitudes for which the associated longitude range
    # is valid. Any granule intersecting the latitude range AND crossing the equator within
    # the longitude range intersects the search area. This is done to avoid the bug reported in CMR-1168.
    #
    def fast_area_crossing_range(lat_range, geometry, ascending, debug=false)
      lat_range = lat_range.to_a
      result = nil
      # There is a separate algorithm for each type of geometry
      if geometry.respond_to? :lat
        # Points
        result = fast_point_crossing_range(lat_range, Coordinate.lat_lon(geometry.lat, geometry.lon), ascending)
      elsif geometry.respond_to? :west
        # Bounding rectangles
        north, west, south, east = geometry.north, geometry.west, geometry.south, geometry.east
        result = fast_bounding_rectangle_crossing_range(lat_range, north, west, south, east, ascending)
      elsif geometry.respond_to? :rings
        # Polygons
        points = geometry.rings.first.points.map {|p| Coordinate.lat_lon(p.lat, p.lon)}
        points << points.first unless points.first.lat == points.last.lat && points.first.lon == points.last.lon
        result = fast_poly_crossing_range(lat_range, points, ascending, debug)
      elsif geometry.respond_to? :points
        # Line strings
        points = geometry.points.map {|p| Coordinate.lat_lon(p.lat, p.lon)}
        result = fast_poly_crossing_range(lat_range, points, ascending)
      else
        # Oops
        raise "Unrecognized geometry: #{geometry.inspect}"
      end
      result
    end

    private

    # Point backtracking.  Just backtrack the single point
    def fast_point_crossing_range(lat_range, point, ascending)
      [[lat_range, coord_crossing_range(point, ascending)]]
    end

    # Bounding box backtracking.
    def fast_bounding_rectangle_crossing_range(lat_range, north_deg, west_deg, south_deg, east_deg, ascending)
      # Convert to radians
      north = north_deg * PI / 180.0
      west = west_deg * PI / 180.0
      south = south_deg * PI / 180.0
      east = east_deg * PI / 180.0

      if west_deg + 360 == east_deg
        # Special case: bounding box spans all longitudes.
        # This would break in the normal algorithm because -180 and 180 are
        # the same latitude and therefore get calculated to represent a
        # bounding box of width 0.
        if south > max_coverage_phi || north < -max_coverage_phi
          return [[lat_range, LongitudeCoverage.none]]
        else
          return [[lat_range, LongitudeCoverage.full]]
        end
      end

      # Special case: the box is above the maximum coverage or below the minimum
      if south > max_coverage_phi || north < -max_coverage_phi
        return [[lat_range, LongitudeCoverage.none]]
      end

      # Special case: part of the box touches an area near the poles which every orbit covers
      if north > full_coverage_phi || south < -full_coverage_phi
        return [[lat_range, LongitudeCoverage.full]]
      end

      # Shift north and south off start latitude a bit if there is an exact match,
      # so that math could work.
      north = start_lat_rad - EPSILON if north == start_lat_rad
      south = start_lat_rad + EPSILON if south == start_lat_rad

      # If the northern part of the bounding rectangle is above the orbit's starting latitude
      # and the southern part is below, split into two bounding rectangles, one entirely above
      # and one entirely below and backtrack each
      if north > start_lat_rad && south < start_lat_rad
        lat_deg = start_lat_rad * 180.0 / PI
        upper_lat_range = [lat_deg + EPSILON, lat_range[1]]
        lower_lat_range = [lat_range[0], lat_deg - EPSILON]
        upper =  fast_bounding_rectangle_crossing_range(upper_lat_range, north_deg, west_deg, lat_deg + EPSILON, east_deg, ascending)
        lower =  fast_bounding_rectangle_crossing_range(lower_lat_range, lat_deg - EPSILON, west_deg, south_deg, east_deg, ascending)
        # move split point to original latitdue
        upper[0][0][0] = start_clat_rad * 180.0/PI + EPSILON
        lower[0][0][1] = start_clat_rad * 180.0/PI - EPSILON
        return upper + lower
      end

      # Special cases out of the way.  Do actual backtracking
      north = [max_coverage_phi - EPSILON, north].min
      south = [-max_coverage_phi + EPSILON, south].max

      # Backtrack each segment
      ll_crossing = coord_crossing_range(Coordinate.phi_theta(south, west), ascending)
      lr_crossing = coord_crossing_range(Coordinate.phi_theta(south, east), ascending)
      ul_crossing = coord_crossing_range(Coordinate.phi_theta(north, west), ascending)
      ur_crossing = coord_crossing_range(Coordinate.phi_theta(north, east), ascending)

      range = LongitudeCoverage.none
      # Combine north and south edge ranges.  The western corner's crossing
      # range will be west of the eastern corner's crossing.
      range << combine_boundary_ranges(ul_crossing, ur_crossing)
      range << combine_boundary_ranges(ll_crossing, lr_crossing)

      # For west and east edges, on the ascending pass, the southern corner's
      # crossing range will be west of the northern corner's crossing range.
      # On the descending pass, it'll be reversed.
      if ascending
        range << combine_boundary_ranges(ll_crossing, ul_crossing)
        range << combine_boundary_ranges(lr_crossing, ur_crossing)
      else
        range << combine_boundary_ranges(ul_crossing, ll_crossing)
        range << combine_boundary_ranges(ur_crossing, lr_crossing)
      end

      # Done!
      [[lat_range, range]]
    end

    # Polygon and Polyline / Linestring backtracking.  Both use the same algorithm.
    # Polygons are closed by having a last point the same as their first (enforced
    # by fast_area_crossing_range)
    def fast_poly_crossing_range(lat_range, points, ascending, debug=false)
      # Easy case first
      return [[lat_range, LongitudeCoverage.full]] if points.any? {|p| p.phi.abs > full_coverage_phi}

      # Create an array of segments, one for each pair of adjacent points in the array.
      segments = points.slice(0..-2).zip(points.slice(1..-1))
      #debug_log "Original:    #{segments.map {|s| s.join(', ')}}"

      # Split segments when a great circle inflection points fall within a segment so
      # all segments are either increasing or decreasing in latitude
      segments = densify_inflection_points(segments)
      #debug_log "Inflections: #{segments.map {|s| s.join(', ')}}"

      # Remove segments whose latitude ranges are entirely out of the coverage range
      segments = remove_no_coverage_segments(segments)
      #debug_log "No coverage: #{segments.map {|s| s.join(', ')}}"

      # Clip remaining segments so that segments which cross the coverage bounds instead
      # stop just inside the coverage bounds
      segments = clip_to_coverage(segments)
      #debug_log "Clipped:     #{segments.map {|s| s.join(', ')}}"

      # Split segments crossing the orbit start latitude so that all segments are
      # entirely above or below the start latitude

      segments = split_at_start_lat(segments)
      #debug_log "Split:       #{segments.map {|s| s.join(', ')}}"

      return [[lat_range, LongitudeCoverage.none]] if segments.empty?

      # Join the ranges from segments that form a contiguous run on one side of the orbit start
      # lat or the other (needed to fix CMR-1168). The approach used here simply starts at the
      # first segment and moves forward until it reaches a segment on the other side of the
      # orbit start lat boundary, at which point a new run is started. This approach is suboptimal
      # in that the first and last segment may end up on two runs that could be merged but will
      # not be. This results in at most one extra run, however, and greatly simplifies the code.
      upper_runs = []
      lower_runs = []
      lat_deg = start_lat_rad * 180.0 / PI
      upper_lat_start = [lat_deg, lat_range[0]].max
      upper_lat_range = [upper_lat_start, lat_range[1]]
      lower_lat_end = [lat_deg, lat_range[1]].min
      lower_lat_range = [lat_range[0], lower_lat_end]
      current_range = debug ? [] : LongitudeCoverage.none
      first_seg = segments[0]
      is_current_range_upper = first_seg[0].phi >= start_lat_rad

      if is_current_range_upper
        upper_runs << current_range
      else
        lower_runs << current_range
      end

      segments.each do |p0, p1|
        combined = segment_crossing_range(p0, p1, ascending)
        if p0.phi < start_lat_rad
          if is_current_range_upper
            current_range =  debug ? [] : LongitudeCoverage.none
            lower_runs << current_range
          end
        else
          if !is_current_range_upper
            current_range = debug ? [] : LongitudeCoverage.none
            upper_runs << current_range
          end
        end

        current_range << (debug ? [p0, p1, combined] : combined)

      end

      # Convert split latitude ranges split latitude back to original collection start latitdue
      start_lat_deg = start_clat_rad * 180.0/PI
      if upper_runs.count > 0 && lower_runs.count > 0
        upper_lat_range[0] = start_lat_deg + EPSILON
        lower_lat_range[1] = start_lat_deg
      end

      # combine ranges with latitudes
      lat_lon_ranges = upper_runs.reduce([]) do |memo, lon_range|
        memo << [upper_lat_range, lon_range]
      end

      lower_runs.reduce(lat_lon_ranges) do |memo, lon_range|
        memo << [lower_lat_range, lon_range]
      end

    end

    # Find the longitude crossing range for the great circle segment defined by p0
    # and p1 for the given pass direction.
    # Figures out which of p0 or p1 will have the westernmost backtracked range,
    # backtracks each, then combines the ranges.
    def segment_crossing_range(p0, p1, ascending)
      # Find the normal
      normal = p0 * p1

      # When normal.phi (the declination of the orbital plane) is negative,
      # the points are oriented east-to-west, so we swap them
      if normal.phi < 0
        normal = -normal
        p0, p1 = p1, p0
      end

      # When normal.phi (now positive) is less than the declination rad, that
      # means that the segment will be intersected by orbits as though it
      # is a vertical edge.  For ascending orbits, the southernmost point
      # will have the westernmost crossing. For descending, the reverse.
      if normal.phi < declination_rad && p1.phi < p0.phi != !ascending
        normal = -normal
        p0, p1 = p1, p0
      end

      # TODO: There is rare a case here where the path is long and slightly shallower than the
      #       orbit declination, causing it to cross the orbit twice because the orbit is offset.
      #       by the Earth's rotation.  The amount potentially missed is small and proportionate
      #       to the length of the edge.
      #       The difference is on the order of 1 degree missed per 90 degrees of arc length.
      #       We should eventually fix this by detecting the case and dividing the line into
      #       segments short enough for the given angular difference that they cannot exhibit
      #       this problem.
      #       For now, I'm ignoring the case, since it is tricky and time consuming for small
      #       benefit, whereas this implementation adds a 5 degrees or more accuracy to the
      #       old one in many more common cases
      #
      #       Example:
      #         Polygon: [[54,29.8125],[86.625,4.78125],[53.296875,71.4375]]
      #         Orbit: Orbits::Orbit.new(98.15, 98.88, 1450, -90, 0.5)
      #         URL: http://edsc.dev/search/map?polygon=54%2C29.8125%2C86.625%2C4.78125%2C53.296875%2C71.4375%2C54%2C29.8125

      #debug_log "Combine: #{p0}, #{p1} (#{normal.lat} vs #{declination_rad * 180.0 / PI})"
      # p0 will have the westernmost crossing, p1 will have the easternmost crossing. Backtrack and combine!
      combine_boundary_ranges(coord_crossing_range(p0, ascending), coord_crossing_range(p1, ascending))
    end

    # Given a list of segments, return a new list of segments whose arcs follow paths of only
    # increasing or decreasing latitude.
    def densify_inflection_points(segments)
      result = []
      segments.each do |p0, p1|
        # If there's an inflection coordinate between p0 and p1, insert segments
        # that start and stop at the inflection point, otherwise pass the original
        # segment.
        inflection = inflection_coords(p0, p1).find { |p| between?(p0, p1, p)}
        if inflection
          result << [p0, inflection]
          result << [inflection, p1]
        else
          result << [p0, p1]
        end
      end
      result
    end

    # Given a list of segments, return a new list of segments which contain no arc that are
    # entirely above or below the orbit's coverage range.
    def remove_no_coverage_segments(segments)
      segments.reject do |p0, p1|
        ((p0.phi >  max_coverage_phi && p1.phi >  max_coverage_phi) ||
         (p0.phi < -max_coverage_phi && p1.phi < -max_coverage_phi))
      end
    end


    # Given a list of segments, return a new list of segments where any segment crossing the
    # orbit's coverage range is clipped to fall entirely within the orbit's coverage range
    def clip_to_coverage(segments)
      segments.map do |p0, p1|
        # First endpoint is above / below the coverage.  Move it within the coverage.
        if p0.phi > max_coverage_phi
          p0 = phi_crossing(p0, p1, max_coverage_phi - EPSILON)
        elsif p0.phi < -max_coverage_phi
          p0 = phi_crossing(p0, p1, -max_coverage_phi + EPSILON)
        end
        # Second endpoint is above / below the coverage.  Move it within the coverage.
        if p1.phi > max_coverage_phi
          p1 = phi_crossing(p0, p1, max_coverage_phi - EPSILON)
        elsif p1.phi < -max_coverage_phi
          p1 = phi_crossing(p0, p1, -max_coverage_phi + EPSILON)
        end
        [p0, p1]
      end
    end

    # Given a list of segments, return a new list of segments where all segments are either
    # entirely above or entirely below the orbit's start latitude.
    def split_at_start_lat(segments)
      result = []
      segments.each do |p0, p1|
        # Be robust against numerical precision problems should a
        # point happen to be exactly at the start lat
        if (p0.phi - start_lat_rad).abs < EPSILON
          offset = p1.phi < p0.phi ? -EPSILON : EPSILON
          p0 = Coordinate.phi_theta(p0.phi + offset, p0.theta)
        end
        if (p1.phi - start_lat_rad).abs < EPSILON
          offset = p0.phi < p1.phi ? -EPSILON : EPSILON
          p1 = Coordinate.phi_theta(p1.phi + offset, p1.theta)
        end

        if p0.phi > start_lat_rad && p1.phi < start_lat_rad
          # p0 is north of the starting latitude and p1 is south
          p = phi_crossing(p0, p1, start_lat_rad)
          result << [p0, Coordinate.phi_theta(p.phi + EPSILON, p.theta)]
          result << [Coordinate.phi_theta(p.phi - EPSILON, p.theta), p1]
        elsif p0.phi < start_lat_rad && p1.phi > start_lat_rad
          # p0 is south of the starting latitude and p1 is north
          p = phi_crossing(p0, p1, start_lat_rad)
          result << [p0, Coordinate.phi_theta(p.phi - EPSILON, p.theta)]
          result << [Coordinate.phi_theta(p.phi + EPSILON, p.theta), p1]
        else
          result << [p0, p1]
        end
      end
      result
    end

    # Return the bearing a traveler at p0 would need to set out at in order
    # to follow the great circle arc toward p1
    def bearing(p0, p1)
      # http://williams.best.vwh.net/avform.htm#Crs

      # Special case: North or South pole
      if cos(p0.phi).abs < EPSILON
        return p0.phi > 0 ? PI : (2 * PI)
      end

      y = sin(p1.theta - p0.theta) * cos(p1.phi)
      x = cos(p0.phi) * sin(p1.phi) - sin(p0.phi) * cos(p1.phi) * cos(p1.theta - p0.theta)
      atan2(y, x)
    end

    def inflection_coords(p0, p1)
      # Special case: p0 and p1 are along the equator.  They have no inflection point.
      if p0.lat == 0 && p1.lat == 0
        return []
      end

      # The formula works to find the maximum inflection coordinate and returns opposite
      # results when the closest inflection point is in the Southern hemisphere.  So,
      # if the closest inflection point is going to be in the Southern hemisphere, we
      # reverse both coordinates, producing the same great circle, but in the Northern
      # hemisphere.
      extreme_point = p0.phi.abs > p1.phi.abs ? p0 : p1
      if extreme_point.phi < 0
        p0 = -p0
        p1 = -p1
      end

      # Clairaut's formula
      # http://www.movable-type.co.uk/scripts/latlong.html
      # http://www.angelfire.com/nt/navtrig/D3.html (Warning: obnoxious sound)
      course = bearing(p0, p1)
      max_phi = acos((sin(course) * cos(p0.phi)).abs)
      dtheta = asin(cos(course) / sin(max_phi))
      dtheta = -dtheta if course < 0
      max_theta = p0.theta + dtheta
      #debug_log "Inflections #{p0} to #{p1} ---> #{[Coordinate.phi_theta(max_phi, max_theta), Coordinate.phi_theta(-max_phi, max_theta + PI)].join(',')}"
      #debug_log "dlon: #{dtheta * 180.0 / PI}, course: #{course * 180.0 / PI}"
      [Coordinate.phi_theta(max_phi, max_theta), Coordinate.phi_theta(-max_phi, max_theta + PI)]
    end

    # Returns true if the longitude of p is between p0 and p1.  The latitude of p is generally
    # ignored but comes into play if p0 and p1 lie on the same longitude circle.  In that case,
    # p is between p0 and p1 if p's longitude is the same as p0 and p1 and p's latitude is along
    # the arc between p0 and p1.
    def between?(p0, p1, p)

      # Special case: p0 and p1 are along the same longitude circle
      dtheta = (p0.theta - p1.theta).abs
      if dtheta < EPSILON
        # Same longitude
        return (p0.theta - p.theta).abs < EPSILON
      elsif (dtheta - PI).abs < EPSILON
        # 180 degrees of longitude apart
        if p0.phi + p1.phi > 0
          # Great circle crosses North Pole
          return ((p0.theta - p.theta).abs < EPSILON && p.phi >= p0.phi || # Due North of p0
                  (p1.theta - p.theta).abs < EPSILON && p.phi >= p1.phi)   # Due North of p1
        else
          # Great circle crosses South Pole
          return ((p0.theta - p.theta).abs < EPSILON && p.phi <= p0.phi || # Due South of p0
                  (p1.theta - p.theta).abs < EPSILON && p.phi <= p1.phi)   # Due South of p1
        end
      end

      theta = p.theta
      min_theta, max_theta = [p0.theta, p1.theta].sort
      min_theta, max_theta = [max_theta, min_theta + 2 * PI] if max_theta - min_theta > PI

      theta += 2 * PI if theta < min_theta

      min_theta <= theta && theta <= max_theta
    end

    # Returns the point along the great circle arc between p0 and p1 with latitude phi or
    # nil if no such point exists.  Note: in practice there could be two such points, so
    # this method is only valid if phi is between the latitudes of p0 and p1
    def phi_crossing(p0, p1, phi)
      # Orient with increasing longitude
      # http://williams.best.vwh.net/avform.htm#Par
      _A_ = sin(p0.phi) * cos(p1.phi) * cos(phi) * sin(p0.theta - p1.theta)
      _B_ = sin(p0.phi) * cos(p1.phi) * cos(phi) * cos(p0.theta - p1.theta) - cos(p0.phi) * sin(p1.phi) * cos(phi)
      _C_ = cos(p0.phi) * cos(p1.phi) * sin(phi) * sin(p0.theta - p1.theta)
      theta = atan2(_B_, _A_)
      if _C_.abs > sqrt(_A_*_A_ + _B_*_B_)
        debug_log "No crossing: #{_C_.abs} > #{sqrt(_A_*_A_ + _B_*_B_)}"
        return nil
      else
        dtheta = acos(_C_ / sqrt(_A_*_A_ + _B_*_B_))
        theta_a = ((p0.theta + dtheta + theta + PI) % (2*PI)) - PI
        theta_b = ((p0.theta - dtheta + theta + PI) % (2*PI)) - PI
        result = Coordinate.phi_theta(phi, theta_a)
        result = Coordinate.phi_theta(phi, theta_b) unless between?(p0, p1, result)
        unless between?(p0, p1, result)
          debug_log "Unexpected nil crossing: #{p0}, #{p1}, #{theta_a * 180.0 / PI} || #{theta_b * 180.0 / PI} at #{phi * 180.0 / PI}N"
          return nil
        end
        result
      end
    end

    # Given two ranges which each contain a single continuous set of longitudes, returns
    # a new range which covers all longitudes from the western edge of the first range eastward
    # to the eastern edge of the second range.
    def combine_boundary_ranges(west_range, east_range)
      # Valid ranges can have either 2 or 4 coverage coordinates.  If they have 4, they're
      # assumed to cross the antimeridian, so the westernmost edge would be the first coordinate
      # of the easternmost segment
      west_edge = west_range.coverage[0] if west_range.coverage.size == 2
      west_edge = west_range.coverage[2] if west_range.coverage.size == 4
      east_edge = east_range.coverage[1] if east_range.coverage.size == 2 || east_range.coverage.size == 4
      if west_edge && east_edge
        LongitudeCoverage.new(west_edge, east_edge)
      else
        LongitudeCoverage.none
      end
    end

    # Orbits have a starting circular latitude which describes the angle around the orbital
    # track at which they start.  This will always be at the same actual latitude.  For
    # splitting segments around the starting circular latitude, it's handy to know that actual
    # latitude.
    def start_lat_rad
      unless @start_lat_rad
        # Take the point at (start_clat, 0) and rotate it around (0, 0) by the declination.
        # The resulting point is where the orbit crossing (0, 0) starts.  The latitude of
        # this point is the geographic start latitude
        transform = Transformation.new
        transform.rotate_x(declination_rad)
        coordinate = transform.apply(Coordinate.phi_theta(start_clat_rad, 0.0))
        @start_lat_rad = coordinate.phi
      end
      @start_lat_rad
    end

    # Useful wrapper allowing us to use the Rails logger, puts, etc as needed
    def debug_log(*args)
      puts(*args)
    end
  end
end
