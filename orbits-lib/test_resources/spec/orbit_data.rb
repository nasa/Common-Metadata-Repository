require 'spec/lib/nsidc-spheres.jar'
require 'java'

module OrbitData
  def self.orbits
    [ { dataset: "C61468210-NSIDC_ECS",
        swath_width: 1450,
        period: 98.88,
        inclination_angle: 98.15,
        number_of_orbits: 0.5,
        start_circular_latitude: -90 },
      { dataset: "C123437689-NSIDC_ECS",
        swath_width: 1600,
        period: 101,
        inclination_angle: 98.62,
        number_of_orbits: 0.5,
        start_circular_latitude: -90 },
      { dataset: "C184503220-NSIDC_ECS",
        swath_width: 2,
        period: 96.7,
        inclination_angle: 94,
        number_of_orbits: 2,
        start_circular_latitude: 50 },
      { dataset: "C184503219-NSIDC_ECS",
        swath_width: 2,
        period: 96.7,
        inclination_angle: 94,
        number_of_orbits: 0.25,
        start_circular_latitude: -50 },
      { dataset: "C184503223-NSIDC_ECS",
        swath_width: 2,
        period: 96.7,
        inclination_angle: 94,
        number_of_orbits: 14,
        start_circular_latitude: 50 },
      { dataset: "C43677744-LARC",
        swath_width: 400,
        period: 98.88,
        inclination_angle: 98.3,
        number_of_orbits: 1,
        start_circular_latitude: 0 },
      { dataset: "C196242137-GSFCS4PA",
        swath_width: 2600,
        period: 100,
        inclination_angle: 98.2,
        number_of_orbits: 1,
        start_circular_latitude: 0 } ]
  end

  def self.to_nsidc(orbit)
    declination = orbit[:inclination_angle] - 90
    period = orbit[:period]
    equator_swath_width_km = orbit[:swath_width]
    start_clat = orbit[:start_circular_latitude]

    construct = Java::NsidcSpheres::Orbit.java_class.constructor(Java::double,Java::double,Java::double,Java::double)
    construct.new_instance(declination, period, equator_swath_width_km, start_clat).to_java
  end

  def self.to_echo(orbit)
    Orbits::Orbit.new(orbit[:inclination_angle],
                      orbit[:period],
                      orbit[:swath_width],
                      orbit[:start_circular_latitude],
                      orbit[:number_of_orbits])
  end

  def self.nsidc_orbits
    orbits.map {|o| to_nsidc(o)}
  end

  def self.echo_orbits
    orbits.map {|o| to_echo(o)}
  end
end

