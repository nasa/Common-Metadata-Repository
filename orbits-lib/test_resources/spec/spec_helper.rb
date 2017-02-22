require 'orbits'

module SpecHelpers
  def self.close_to?(actual, expected)
    # The amount of error we tolerate.  This is a made up number
    # that lets our tests pass.  Nearly all of our tests pass
    # with a much higher precision, but we have one where the
    # legacy calculation and our calculation are close to 0 which
    # requires us to use a looser tolerance
    tolerance = 0.002
    if expected == 0
      actual.abs < tolerance
    else
      (1 - actual / expected).abs < tolerance
    end
  end
end

RSpec::Matchers.define :be_close_to do |expected|
  match do |actual|
    SpecHelpers.close_to? actual, expected
  end
end

RSpec::Matchers.define :be_lat_lon do |expected_lat, expected_lon|
  match do |actual|
    # There's some floating point error in converting coordinates, so
    # this makes assertions cleaner
    (SpecHelpers.close_to?(actual.lat, expected_lat) &&
     SpecHelpers.close_to?(actual.lon, expected_lon))
  end

  failure_message_for_should do |actual|
    "expected (#{actual.lat}, #{actual.lon}) to be (#{expected[0]}, #{expected[1]})"
  end
end

RSpec::Matchers.define :be_lat_lons do |expecteds|
  match do |actuals|
    result = true
    actuals.zip(expecteds).each do |actual, expected|
      expected_lat, expected_lon = *expected
      result &&= SpecHelpers.close_to?(actual.lat, expected_lat)
      result &&= SpecHelpers.close_to?(actual.lon, expected_lon)
    end
    result
  end

  failure_message_for_should do |actual|
    actual_s = actual.map {|c| "(#{c.lat.round(3)}, #{c.lon.round(3)})"}.join(', ')
    expected_s = expecteds.map {|lat, lon| "(#{lat}, #{lon})"}.join(', ')
    "expected [#{actual_s}] to be [#{expected_s}]"
  end

  failure_message_for_should_not do |actual|
    actual_s = actual.map {|c| "(#{c.lat.round(3)}, #{c.lon.round(3)})"}.join(', ')
    expected_s = expecteds.map {|lat, lon| "(#{lat}, #{lon})"}.join(', ')
    "expected [#{actual_s}] to not be [#{expected_s}]"
  end
end

RSpec::Matchers.define :be_separated_by_at_most do |angle|
  expected_cos = Math::cos(angle)

  match do |coords|
    result = true

    pairs = coords.zip(coords[1..-1] + [coords.first])
    pairs.each do |a, b|
      actual_cos = a.dot b
      result &&= actual_cos > expected_cos || SpecHelpers.close_to?(actual_cos, expected_cos)
    end
    result
  end
end

RSpec::Matchers.define :fall_on_the_polygon_defined_by do |corners|
  match do |coords|
    corners = corners.map {|lat, lon| Orbits::Coordinate.lat_lon(lat, lon)}
    corner_pairs = corners.zip(corners[1..-1] + [corners.first])

    coords = coords.dup

    result = true

    corner_pairs.each do |p1, p2|
      expected_normal = p1 * p2

      coord = coords.shift
      while coord.dot(p2) < 0.99
        actual_normal = coord * p2
        result &&= actual_normal.dot(expected_normal) > 0.99
        coord = coords.shift
      end
    end
    result
  end
end
