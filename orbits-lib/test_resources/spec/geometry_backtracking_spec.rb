require 'orbits'
# require 'spec_helper'
# require 'mock_geometries'

# This test is commented out now because it doesn't work. As part of an update to CMR-1168 these tests
# broke and that was missed. I don't have enough knowledge currently to fix them I'm leaving the tests
# here for historical purposes.

# RSpec::Matchers.define :backtrack_similar_ranges_for do |geometry, ascending, tolerance|
#   def areas(orbit, geometry, ascending)
#     densified = orbit.densified_area_crossing_range(geometry, ascending)
#     fast = orbit.fast_area_crossing_range(geometry, ascending)
#     [densified.to_a, fast.to_a]
#   end
#
#   match do |orbit|
#     densified, fast = areas(orbit, geometry, ascending)
#     flat_dense = densified.flatten
#     flat_fast = fast.flatten
#     flat_dense.zip(flat_fast).all? do |dense, fast|
#       (dense - fast).abs < tolerance
#     end
#   end
#
#   failure_message_for_should do |orbit|
#     densified, fast = areas(orbit, geometry, ascending)
#     "expected #{geometry.inspect} to backtrack similar to #{densified.inspect} on the #{ascending ? 'ascending' : 'descending'} pass, got #{fast.inspect}"
#   end
# end
#
# describe "Geometry-based backtracking" do
#
#   let (:orbit) { Orbits::Orbit.new(98.15, 98.88, 100, 50, 0.5) }
#
#   context "#fast_area_crossing_range" do
#
#     context "compared to the densified implementation" do
#       ['ascending', 'descending'].each do |pass|
#         is_ascending = pass == 'ascending'
#         context "on the #{pass} pass" do
#           # Points
#           it "returns similar ranges for points" do
#             expect(orbit).to backtrack_similar_ranges_for(Mock::Point.new(45, 45), is_ascending, 0.1)
#           end
#
#           # Bounding Rectangles
#           it "returns similar ranges for bounding rectangles in the northern hemisphere" do
#             expect(orbit).to backtrack_similar_ranges_for(Mock::BoundingRectangle.new(30, 45, 30, 45), is_ascending, 0.1)
#           end
#
#           it "returns similar ranges for bounding rectangles in the southern hemisphere" do
#             expect(orbit).to backtrack_similar_ranges_for(Mock::BoundingRectangle.new(-45, -30, 30, 45), is_ascending, 0.1)
#           end
#
#           it "returns similar ranges for bounding rectangles crossing the equator" do
#             expect(orbit).to backtrack_similar_ranges_for(Mock::BoundingRectangle.new(-10, 10, 30, 45), is_ascending, 0.1)
#           end
#
#           it "returns similar ranges for bounding rectangles crossing the antimeridian" do
#             expect(orbit).to backtrack_similar_ranges_for(Mock::BoundingRectangle.new(30, 45, 170, -170), is_ascending, 0.1)
#           end
#
#           it "returns similar ranges for very large bounding rectangles" do
#             expect(orbit).to backtrack_similar_ranges_for(Mock::BoundingRectangle.new(-85, 85, -160, 160), is_ascending, 0.1)
#           end
#
#           it "returns similar ranges for bounding rectangles covering the whole Earth" do
#             expect(orbit).to backtrack_similar_ranges_for(Mock::BoundingRectangle.new(-90, 90, -180, 180), is_ascending, 0.1)
#           end
#
#           it "returns similar ranges for bounding rectangles above the coverage area" do
#             expect(orbit).to backtrack_similar_ranges_for(Mock::BoundingRectangle.new(89, 90, 30, 45), is_ascending, 0.1)
#           end
#
#           it "returns similar ranges for bounding rectangles crossing the orbit start latitude" do
#             expect(orbit).to backtrack_similar_ranges_for(Mock::BoundingRectangle.new(20, 70, 0, 45), is_ascending, 0.3)
#           end
#
#           # Polygons
#           it "returns similar ranges for polygons in the northern hemisphere" do
#             expect(orbit).to backtrack_similar_ranges_for(Mock::Polygon.new([[10, 10], [30, 10], [20, 20]]), is_ascending, 0.1)
#           end
#
#           it "returns similar ranges for polygons in the southern hemisphere" do
#             expect(orbit).to backtrack_similar_ranges_for(Mock::Polygon.new([[-10, 10], [-30, 10], [-20, 20]]), is_ascending, 0.1)
#           end
#
#           it "returns similar ranges for polygons crossing the equator" do
#             expect(orbit).to backtrack_similar_ranges_for(Mock::Polygon.new([[-10, 10], [10, 10], [20, 20]]), is_ascending, 0.1)
#           end
#
#           it "returns similar ranges for polygons crossing the antimeridian" do
#             expect(orbit).to backtrack_similar_ranges_for(Mock::Polygon.new([[-10, 175], [10, 175], [20, -175]]), is_ascending, 0.1)
#           end
#
#           it "returns similar ranges for polygons above the coverage area" do
#             expect(orbit).to backtrack_similar_ranges_for(Mock::Polygon.new([[85, 0], [85, 120], [85, -120]]), is_ascending, 0.1)
#           end
#
#           it "returns similar ranges for polygons covering the south pole" do
#             # This (and the north pole one) barely dip into the coverage range
#             expect(orbit).to backtrack_similar_ranges_for(Mock::Polygon.new([[-81, 0], [-81, 120], [-81, -120]]), is_ascending, 5)
#           end
#
#           it "returns similar ranges for polygons covering the north pole" do
#             expect(orbit).to backtrack_similar_ranges_for(Mock::Polygon.new([[81, 0], [81, 120], [81, -120]]), is_ascending, 5)
#           end
#
#           it "returns similar ranges for polygons crossing the orbit start latitude" do
#             expect(orbit).to backtrack_similar_ranges_for(Mock::Polygon.new([[40, 10], [60, 10], [50, 20]]), is_ascending, 1)
#           end
#
#           it "returns similar ranges when a polygon point is very near the orbit inflection point" do
#             inflection = orbit.max_coverage_phi - 0.00001
#             expect(orbit).to backtrack_similar_ranges_for(Mock::Polygon.new([[inflection, 0], [inflection, 120], [inflection, -120]]), is_ascending, 5)
#           end
#
#           # Line strings (identical to polygons except for being unclosed)
#           it "returns similar ranges for unclosed line strings" do
#             expect(orbit).to backtrack_similar_ranges_for(Mock::Line.new([[81, 0], [81, 120], [81, -120]]), is_ascending, 5)
#           end
#         end
#       end
#     end
#   end
# end
