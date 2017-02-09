require 'spec/spec_helper'
require 'orbits/longitude_coverage'

include Math

describe Orbits::LongitudeCoverage do
  MIN = Orbits::LongitudeCoverage::MIN
  MAX = Orbits::LongitudeCoverage::MAX

  describe "self#full" do
    it "returns full earth coverage" do
      coverage = Orbits::LongitudeCoverage.full
      coverage.coverage.should == [MIN, MAX]
    end
  end

  describe "self#none" do
    it "returns an empty coverage" do
      coverage = Orbits::LongitudeCoverage.none
      coverage.coverage.should == []
    end
  end

  describe "#initialize" do
    it "initializes empty coverage when given no parameters" do
      coverage = Orbits::LongitudeCoverage.new
      coverage.coverage.should == []
    end

    it "initializes empty coverage range when given one parameter" do
      coverage = Orbits::LongitudeCoverage.new(0)
      coverage.coverage.should == []
      coverage = Orbits::LongitudeCoverage.new(nil, 0)
      coverage.coverage.should == []
    end

    it "sets an initial coverage range when given two parameters" do
      coverage = Orbits::LongitudeCoverage.new(0, PI / 2)
      coverage.coverage.should == [0, PI / 2]
    end
  end

  describe "#add" do
    context "full coverage" do
      it "replaces existing coverage when adding full coverage" do
        coverage = Orbits::LongitudeCoverage.new(0, PI / 2)
        coverage.add 0, 2 * PI
        coverage.coverage.should == [MIN, MAX]
      end

      it "adds full coverage if max exceeds the min by at least a full rotation" do
        coverage = Orbits::LongitudeCoverage.new
        coverage.add 0, 2 * PI
        coverage.coverage.should == [MIN, MAX]

        coverage = Orbits::LongitudeCoverage.new
        coverage.add 0, 3 * PI
        coverage.coverage.should == [MIN, MAX]
      end

      it "doesn't add full coverage if min exceeds the max by at least a full rotation" do
        coverage = Orbits::LongitudeCoverage.new
        coverage.add 3 * PI, 0
        coverage.coverage.should == [-PI, 0]
      end

      it "adds full coverage if min and max are equal when normalized" do
        coverage = Orbits::LongitudeCoverage.new
        coverage.add 0, 0
        coverage.coverage.should == [MIN, MAX]

        coverage = Orbits::LongitudeCoverage.new
        coverage.add 2 * PI, 0
        coverage.coverage.should == [MIN, MAX]
      end
    end

    context "partial coverage" do
      before do
        @coverage = Orbits::LongitudeCoverage.new
        @coverage.add -PI / 3, -PI / 6
        @coverage.add PI / 6, PI / 3
      end

      it "doesn't change the coverage range if the added range is covered" do
        @coverage.add -PI / 4, -PI / 5
        @coverage.coverage.should == [-PI / 3, -PI / 6,
                                     PI / 6, PI / 3]
      end

      it "adds coverage range if the added range is not covered" do
        @coverage.add PI / 2, PI
        @coverage.coverage.should == [-PI / 3, -PI / 6,
                                     PI / 6, PI / 3,
                                     PI / 2, PI]
      end

      it "adds two coverage areas when wrapping the dateline" do
        @coverage.add PI / 2, -PI / 2
        @coverage.coverage.should == [-PI, -PI / 2,
                                     -PI / 3, -PI / 6,
                                     PI / 6, PI / 3,
                                     PI / 2, PI]
      end

      it "extends coverage areas that overlap on the left" do
        @coverage.add PI / 4, PI / 2
        @coverage.coverage.should == [-PI / 3, -PI / 6,
                                     PI / 6, PI / 2]
      end

      it "extends coverage areas that overlap on the right" do
        @coverage.add -PI / 2, -PI / 4
        @coverage.coverage.should == [-PI / 2, -PI / 6,
                                     PI / 6, PI / 3]
      end

      it "fills in holes in coverage areas when the holes are overlapped" do
        @coverage.add -PI / 4, PI / 4
        @coverage.coverage.should == [-PI / 3, PI / 3]
      end

      it "extends coverage areas whose edges touch" do
        @coverage.add -PI / 2, -PI / 3
        @coverage.coverage.should == [-PI / 2, -PI / 6,
                                     PI / 6, PI / 3]
        @coverage.add PI / 3, PI / 2
        @coverage.coverage.should == [-PI / 2, -PI / 6,
                                     PI / 6, PI / 2]
      end

      it "removes coverage areas that are completely overlapped" do
        @coverage.add PI / 7, PI / 2
        @coverage.coverage.should == [-PI / 3, -PI / 6,
                                     PI / 7, PI / 2]
        @coverage.add -PI / 2, PI
        @coverage.coverage.should == [-PI / 2, PI]
      end

      it "does not alter the coverage when a range is duplicated" do
        @coverage.add PI / 6, PI / 3
        @coverage.coverage.should == [-PI / 3, -PI / 6,
                                     PI / 6, PI / 3]
      end
    end
  end
end

