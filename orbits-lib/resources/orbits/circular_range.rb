module Spatial
  # Represents a range of numbers on a circle, where the extents of the range
  # get repeated every 360 degrees.  Minimum and maximum values are expressed
  # in degrees and are stored in a normalized manner.  The "min" field contains
  # the smallest positive value congruent to the range's minimum.  The "max"
  # field contains the smallest value larger than "min" congruent to the range's
  # maximum.
  class CircularRange
    attr_reader :min, :max

    # Initialize the range.  See class documentation for how min and max are
    # normalized.  Note that we assume that ranges never span more than 360
    # degrees.  So [0, 361] is congruent to [0, 1], covering 1 degree, not 360.
    # If max is less than min, we assume that the range wraps around the circle.
    def initialize(min, max)
      @min = min % 360
      @max = @min + ((max - min == 360) ? 360 : (max - min) % 360)
    end

    # Returns an array containing all min..max ranges crossing
    # the range from lower_bound to upper_bound.  For instance,
    # >> range = Spatial::CircularRange.new(0, 45)
    # >> range.denormalize(-180, 540)
    # => [0..45, 360..405]
    def denormalize(lower_bound, upper_bound)
      # The first and last multiple of 360 we need to shift our range by
      start = 360 * ((lower_bound - @max).to_i / 360 + 1)
      stop  = 360 * ((upper_bound - @min).to_i / 360 + 1)

      (start...stop).step(360).map { |s| (@min + s)..(@max + s) }
    end
  end
end

