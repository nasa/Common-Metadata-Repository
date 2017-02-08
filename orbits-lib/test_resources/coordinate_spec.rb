require 'spec/spec_helper'
require 'orbits/coordinate'

describe "Coordinate" do
  it "constructs coordinates from x, y, and z" do
    x = -0.5
    y = 0.5
    z = Math.sqrt(2) / 2
    coord = Orbits::Coordinate.xyz(x, y, z)
    coord.x.should be_close_to x
    coord.y.should be_close_to y
    coord.z.should be_close_to z
  end

  it "constructs coordinates from latitude and longitude" do
    coord = Orbits::Coordinate.lat_lon(40, 60)
    coord.lat.should be_close_to 40
    coord.lon.should be_close_to 60
  end

  it "constructs coordinates from phi and theta" do
    coord = Orbits::Coordinate.phi_theta(0.5, 0.6)
    coord.phi.should be_close_to 0.5
    coord.theta.should be_close_to 0.6
  end

  it "converts lat and lon to phi and theta" do
    coord = Orbits::Coordinate.lat_lon(45, 135)
    coord.phi.should be_close_to Math::PI / 4
    coord.theta.should be_close_to 3 * Math::PI / 4
  end

  it "converts lat and lon to x, y, and z" do
    coord = Orbits::Coordinate.lat_lon(45, 135)
    coord.x.should be_close_to -0.5
    coord.y.should be_close_to 0.5
    coord.z.should be_close_to Math.sqrt(2) / 2
  end

  it "converts phi and theta to lat and lon" do
    coord = Orbits::Coordinate.phi_theta(Math::PI / 4, 3 * Math::PI / 4)
    coord.lat.should be_close_to 45
    coord.lon.should be_close_to 135
  end

  it "converts phi and theta to x, y, and z" do
    coord = Orbits::Coordinate.phi_theta(Math::PI / 4, 3 * Math::PI / 4)
    coord.x.should be_close_to -0.5
    coord.y.should be_close_to 0.5
    coord.z.should be_close_to Math.sqrt(2) / 2
  end

  it "converts x, y, and z to lat and lon" do
    coord = Orbits::Coordinate.xyz(-0.5, 0.5, Math.sqrt(2) / 2)
    coord.lat.should be_close_to 45
    coord.lon.should be_close_to 135
  end

  it "converts x, y, and z to phi and theta" do
    coord = Orbits::Coordinate.xyz(-0.5, 0.5, Math.sqrt(2) / 2)
    coord.phi.should be_close_to Math::PI / 4
    coord.theta.should be_close_to 3 * Math::PI / 4
  end

  it "normalizes x, y, and z" do
    coord = Orbits::Coordinate.xyz(-1, 1, Math.sqrt(2))
    coord.x.should be_close_to -0.5
    coord.y.should be_close_to 0.5
    coord.z.should be_close_to Math.sqrt(2) / 2
  end

  it "normalizes latitude to the interval [-90, 90]" do
    Orbits::Coordinate.lat_lon(90, 0).lat.should be_close_to 90
    Orbits::Coordinate.lat_lon(91, 0).lat.should be_close_to 89
    Orbits::Coordinate.lat_lon(91, 0).lon.should be_close_to -180
    Orbits::Coordinate.lat_lon(180, 0).lat.should be_close_to 0
    Orbits::Coordinate.lat_lon(451, 0).lat.should be_close_to 89

    Orbits::Coordinate.lat_lon(-90, 0).lat.should be_close_to -90
    Orbits::Coordinate.lat_lon(-91, 0).lat.should be_close_to -89
    Orbits::Coordinate.lat_lon(-91, 0).lon.should be_close_to -180
    Orbits::Coordinate.lat_lon(-180, 0).lat.should be_close_to 0
    Orbits::Coordinate.lat_lon(-451, 0).lat.should be_close_to -89
  end

  it "normalizes longitude to the interval [-180, 180)" do
    Orbits::Coordinate.lat_lon(0, 179).lon.should be_close_to 179
    Orbits::Coordinate.lat_lon(0, 180).lon.should be_close_to -180
    Orbits::Coordinate.lat_lon(0, 360).lon.should be_close_to 0
    Orbits::Coordinate.lat_lon(0, 540).lon.should be_close_to -180

    Orbits::Coordinate.lat_lon(0, -179).lon.should be_close_to -179
    Orbits::Coordinate.lat_lon(0, -180).lon.should be_close_to -180
    Orbits::Coordinate.lat_lon(0, -360).lon.should be_close_to 0
    Orbits::Coordinate.lat_lon(0, -540).lon.should be_close_to -180
  end

  it "normalizes phi to the interval [-PI / 2, PI / 2]" do
    pi = Math::PI
    d = 0.001 # A small delta

    Orbits::Coordinate.phi_theta(pi / 2, 0).phi.should be_close_to pi / 2
    Orbits::Coordinate.phi_theta(pi / 2 + d, 0).phi.should be_close_to pi / 2 - d
    Orbits::Coordinate.phi_theta(pi / 2 + d, 0).theta.should be_close_to -pi
    Orbits::Coordinate.phi_theta(pi, 0).phi.should be_close_to 0
    Orbits::Coordinate.phi_theta(5 * pi / 2 + d, 0).phi.should be_close_to pi / 2 - d

    Orbits::Coordinate.phi_theta(-pi / 2, 0).phi.should be_close_to -pi / 2
    Orbits::Coordinate.phi_theta(-pi / 2 - d, 0).phi.should be_close_to -pi / 2 + d
    Orbits::Coordinate.phi_theta(-pi / 2 - d, 0).theta.should be_close_to -pi
    Orbits::Coordinate.phi_theta(-pi, 0).phi.should be_close_to 0
    Orbits::Coordinate.phi_theta(-5 * pi / 2 - d, 0).phi.should be_close_to -pi / 2 + d
  end

  it "normalizes theta to the interval [-PI, PI)" do
    pi = Math::PI
    d = 0.001 # A small delta

    Orbits::Coordinate.phi_theta(0, pi - d).theta.should be_close_to pi - d
    Orbits::Coordinate.phi_theta(0, pi).theta.should be_close_to -pi
    Orbits::Coordinate.phi_theta(0, 2 * pi).theta.should be_close_to 0
    Orbits::Coordinate.phi_theta(0, 3 * pi).theta.should be_close_to -pi

    Orbits::Coordinate.phi_theta(0, -pi + d).theta.should be_close_to -pi + d
    Orbits::Coordinate.phi_theta(0, -pi).theta.should be_close_to -pi
    Orbits::Coordinate.phi_theta(0, -2 * pi).theta.should be_close_to 0
    Orbits::Coordinate.phi_theta(0, -3 * pi).theta.should be_close_to -pi
  end
end

