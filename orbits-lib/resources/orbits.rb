require 'orbits/coordinate'
require 'orbits/transformation'
require 'orbits/geometry_to_coordinates_converter'
require 'orbits/longitude_coverage'
require 'orbits/orbit'
require 'orbits/orbit_geometry'

module Orbits
  SOLAR_DAY_S = 24 * 60 * 60
  SIDEREAL_DAY_S = 86164.0905
  TWO_PI = 2 * Math::PI
  # TODO It's unclear whether this should be a day or sidereal day.  The original
  #      code mentions only working for sun-synchronous orbits
  EARTH_ANGULAR_VELOCITY_RAD_S = TWO_PI / SOLAR_DAY_S;
  EARTH_ANGULAR_VELOCITY_DEG_S = EARTH_ANGULAR_VELOCITY_RAD_S * 180.0 / Math::PI

  # Since the Earth isn't a sphere, there's no correct radius.  We'll use NSIDC's for now
  GOOGLE_EARTH_RADIUS_M = 6378100.0
  NSIDC_EARTH_RADIUS_M = 6367435.0
  MEAN_SPHERICAL_EARTH_RADIUS_M = 6371000.0

  EARTH_RADIUS_M = NSIDC_EARTH_RADIUS_M
end
