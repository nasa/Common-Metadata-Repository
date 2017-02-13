module Orbits
  class LongitudeCoverage
    include Math

    MIN = - PI
    MAX = PI
    MOD = MAX - MIN

    attr_reader :coverage

    def self.full
      new(0, 0)
    end

    def self.none
      new
    end

    def initialize(min=nil, max=nil)
      @coverage = []
      add(min, max) unless min.nil? || max.nil?
    end

    def add(theta0, theta1)
      if theta1 - theta0 > MOD
        # Full coverage
        @coverage = [MIN, MAX]
        return
      end
      theta0 = normalize(theta0)
      theta1 = normalize(theta1)
      if theta0 == theta1
        # Full coverage again
        @coverage = [MIN, MAX]
        return
      end
      if theta1 < theta0
        # Add the range from [MIN, theta1)
        add(MIN, theta1) unless theta1 == MIN
        # Then add the remainder of the range from [theta0, MAX)
        theta1 = MAX
      end

      i0 = insert_index(theta0)
      i1 = insert_index(theta1)

      if @coverage.size > 0
        lon0 = theta0 * 180 / Math::PI
        lon1 = theta1 * 180 / Math::PI
      end

      # If theta1 touches another interval, they don't overlap,
      # but we want to merge them anyways
      i1 += 1 if @coverage[i1] == theta1

      # If the end points are already covered, don't insert them
      insert = []
      insert << theta0 unless i0.odd?
      insert << theta1 unless i1.odd?

      @coverage.slice!(i0, i1 - i0)
      @coverage.insert(i0, *insert)
      nil
    end

    def <<(other)
      other.coverage.each_slice(2) do |min, max|
        add(min, max)
      end
    end

    def to_a
      result = []
      @coverage.each_slice(2) do |min, max|
        result << [min * 180 / PI, max * 180 / PI]
      end
      if @coverage.first == -PI && @coverage.last == PI && @coverage.size > 2
        result.first[0] = result.last[0]
        result.first[1] += 360
        result.pop
      end
      result
    end

    def to_s
      array = to_a
      if array.size == 0
        "[        ,         )"
      else
        to_a.map {|min, max| "[%8.3f, %8.3f)" % [min, max]}.join(", ")
      end
    end

    private

    # Move theta into the interval [MIN, MAX)
    def normalize(theta)
      theta -= MOD while theta >= MAX
      theta += MOD while theta < MIN
      theta
    end

    def insert_index(theta)
      upper = @coverage.size - 1
      lower = 0

      i = 0
      while upper >= lower
        i = lower + (upper - lower) / 2
        case theta <=> @coverage[i]
        when 0 then return i
        when 1 then lower = i + 1
        else        upper = i - 1
        end
      end
      # Handle the edge case where i is the largest value in the list
      i += 1 if @coverage[i] && theta > @coverage[i]
      i
    end
  end
end
