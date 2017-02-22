require 'matrix'
require 'orbits/coordinate'

module Orbits
  class Transformation
    include Math # heh

    def self.identity
      new
    end

    def initialize(matrix=Matrix.identity(3))
      @matrix = matrix
    end

    # Right-handed coordinate system, rotations appear counter-clockwise
    # when their axis points at the observer
    def rotate_x(t)
      sin_t = sin(t)
      cos_t = cos(t)
      @matrix *= Matrix.rows(
        [[1,     0,      0],
         [0, cos_t, -sin_t],
         [0, sin_t,  cos_t]])
      self
    end

    def rotate_y(t)
      sin_t = sin(t)
      cos_t = cos(t)
      @matrix *= Matrix.rows(
        [[ cos_t, 0, sin_t],
         [     0, 1,     0],
         [-sin_t, 0, cos_t]])
      self
    end

    def rotate_z(t)
      sin_t = sin(t)
      cos_t = cos(t)
      @matrix *= Matrix.rows(
        [[cos_t, -sin_t, 0],
         [sin_t,  cos_t, 0],
         [    0,      0, 1]])
      self
    end

    # Note: this seems to suffer from significant precision errors
    def self.great_circle_rotation(coord_a, coord_b, t)
      # Compute the cross product vector, u
      u = coord_a * coord_b

      # http://en.wikipedia.org/wiki/Rotation_matrix#Rotation_matrix_from_axis_and_angle

      m_cross_u = Matrix.rows(
        [[   0, -u.z,  u.y],
         [ u.z,    0, -u.x],
         [-u.y,  u.x,    0]])

      m_tensor_u = Matrix.rows(
        [[u.x * u.x, u.x * u.y,  u.x * u.z],
         [u.y * u.x, u.y * u.y,  u.y * u.z],
         [u.z * u.x, u.z * u.y,  u.z * u.z]])

      m_cos_t = Matrix.scalar(3, Math::cos(t))
      m_sin_t = Matrix.scalar(3, Math::sin(t))
      m_1_cos_t = Matrix.scalar(3, 1 - Math::cos(t))
      m_ident = Matrix.identity(3)

      new(m_cos_t + m_sin_t * m_cross_u + (m_1_cos_t) * m_tensor_u)
    end

    def apply(coord)
      vector = @matrix * Vector[coord.x, coord.y, coord.z]
      Coordinate.xyz(vector[0], vector[1], vector[2])
    end
  end
end
