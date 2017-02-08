
require 'orbits/coordinate'



module Orbits
  class OrbitGeometry

  	include Math

  	DELTA = 0.00000001
  	attr_reader :orbit

	def initialize(orbit)
      @orbit = orbit
	end

	def ground_track(equator_ascending_crossing_longitude_deg, time_elapsed_min)
      time_elapsed_s = time_elapsed_min * 60
      equator_ascending_crossing_longitude_rad = equator_ascending_crossing_longitude_deg * PI/180
      coord = ground_track_uncorrected(equator_ascending_crossing_longitude_rad, (time_elapsed_s * orbit.angular_velocity_rad_s) % (2 * PI))
      Coordinate.phi_theta(coord.phi, coord.theta - longitude_correction(time_elapsed_s))
    end

    def along_track_swath_edges(equator_ascending_crossing_longitude_deg, time_elapsed_min)
      time_elapsed_s = time_elapsed_min * 60
      equator_ascending_crossing_longitude_rad = equator_ascending_crossing_longitude_deg * PI/180
      alpha = (time_elapsed_s * orbit.angular_velocity_rad_s) % (2 * PI)
      alpha = alpha + DELTA if alpha == 0 || alpha == PI

      r = orbit.swath_width_rad/2
      coord = ground_track_uncorrected(0, alpha)
      beta = Math::acos(cos(r) * cos(coord.phi) * cos(coord.theta))
      rR = asin(sin(r)/sin(beta))

      lat_left =   Math::asin(sin(rR + orbit.inclination_rad) * sin(beta)) * ((alpha <= PI) ? 1 : -1)
      rw = acos(cos(r) * cos(coord.phi) * cos(coord.theta)/cos(lat_left))
      lon_left = equator_ascending_crossing_longitude_rad - (alpha <= PI ? rw : 2 * PI - rw) * (rR + orbit.inclination_rad < PI/2 ? -1 : 1)

      lat_right = Math::asin(sin(-rR + orbit.inclination_rad) * sin(beta)) * ((alpha <= PI) ? 1 : -1)
      re = acos(cos(r) * cos(coord.phi) * cos(coord.theta)/cos(lat_right))
      lon_right = equator_ascending_crossing_longitude_rad -  (alpha <= PI ? re : 2 * PI - re) * (-rR + orbit.inclination_rad < PI/2 ? -1 : 1) 

      edge = [ Coordinate.phi_theta(lat_left, lon_left - longitude_correction(time_elapsed_s)),  Coordinate.phi_theta(lat_right, lon_right - longitude_correction(time_elapsed_s))]
      
      if(alpha < PI)
      	edge
      else
      	edge.reverse
      end

    end

    private

    def ground_track_uncorrected(equator_ascending_crossing_longitude_rad, alpha)
      ground_track_latitude = Math.asin(sin(orbit.inclination_rad) * sin(alpha))
      
      if(ground_track_latitude.abs == PI/2)
        ground_track_longitude = equator_ascending_crossing_longitude_rad
      else
      	drift = acos(cos(alpha)/cos(ground_track_latitude))
        ground_track_longitude = equator_ascending_crossing_longitude_rad -  (alpha <= PI ? drift : 2 * PI - drift) * (orbit.retrograde? ? 1 : -1)
      end
      Coordinate.phi_theta(ground_track_latitude, ground_track_longitude)
    end

    def longitude_correction(time_elapsed_s)
      (2 * PI/Orbits::SOLAR_DAY_S) * time_elapsed_s % (2*Math::PI)
    end

    def acos(val)
      theta = Math::acos(val >=0 ? [val, 1].min : [val, -1].max)
    end

    def asin(val)
      theta = Math::asin(val >=0 ? [val, 1].min : [val, -1].max)
    end

  end
end