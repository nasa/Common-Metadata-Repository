(ns cmr.spatial.cartesian-ring
  (:require [cmr.spatial.point :as p]
            [cmr.spatial.math :refer :all]
            [cmr.common.util :as util]
            [primitive-math]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.conversion :as c]
            [cmr.spatial.line-segment :as s]
            [cmr.spatial.derived :as d]
            [clojure.math.combinatorics :as combo]
            [cmr.spatial.validation :as v]
            [cmr.spatial.messages :as msg]
            [cmr.spatial.arc-line-segment-intersections :as asi]
            [cmr.common.dev.record-pretty-printer :as record-pretty-printer])
  (:import cmr.spatial.arc.Arc))
(primitive-math/use-primitive-operators)

(def external-point
  "Defines a point external to all cartesian rings. It works because it's outside the area of the
  earth."
  (p/point 181 91))

(defrecord CartesianRing
  [
   ;; The points that make up the ring. Points must be in counterclockwise order. The last point
   ;; must match the first point.
   points

   ;; Derived fields

   ;; A set of the unique points in the ring.
   ;; This should be used as opposed to creating a set from the points many times over which is expensive.
   point-set

   ;; Line segments of the ring
   line-segments

   ;; the minimum bounding rectangle
   mbr
   ])
(record-pretty-printer/enable-record-pretty-printing CartesianRing)

(defn covers-point?
  "Determines if a ring covers the given point. The algorithm works by counting the number of times
  an arc between the point and a known external point crosses the ring. An even count means the point
  is external. An odd count means the point is inside the ring."
  [ring point]

  ;; Only do real intersection if the mbr covers the point.
  (when (mbr/covers-point? :cartesian (:mbr ring) point)
    (if (some (:point-set ring) point)
      true ; The point is actually one of the rings points
      ;; otherwise we'll do the real intersection algorithm
      (let [;; Create the test segment
            crossing-line (s/line-segment point external-point)
            ;; Find all the points the line passes through
            intersections (filter identity
                                  (map #(s/intersection % crossing-line) (:line-segments ring)))
            ;; Round the points. If the crossing arc passes through a point on the ring the
            ;; intersection algorithm will result in two very, very close points. By rounding to
            ;; within an acceptable range they'll be seen as the same point.
            intersections (set (map (partial p/round-point 5) intersections))]
        (or (odd? (count intersections))
            ;; if the point itself is one of the intersections then the ring covers it
            (intersections point))))))

(defn ring
  "Creates a new ring with the given points. If the other fields of a ring are needed. The
  calculate-derived function should be used to populate it."
  [points]
  (->CartesianRing points nil nil nil))

(defn ring->line-segments
  "Determines the line-segments from the points in the ring."
  [^CartesianRing ring]
  (or (.line_segments ring)
      (s/points->line-segments (.points ring))))

(defn ring->mbr
  "Determines the mbr from the points in the ring."
  [^CartesianRing ring]
  (or (.mbr ring)
      (let [line-segments (ring->line-segments ring)]
        (->> line-segments (map :mbr) (reduce #(mbr/union %1 %2 false))))))

(defn course-rotation-direction
  "Calculates the rotation direction of the arcs of a ring. Will be one of :clockwise,
  :counter_clockwise, or :none.

  It works by calculating the number of degrees of turning that the ring does. It gets course from
  each line segment. It determines how many degrees each turn is. Turns to the left,
  counter clockwise, are positive. Turns to the right, clockwise, are negative. This adds all of the
  differences together to get the net bearing change while traveling around the ring. A normal
  counter clockwise ring will be approximately 360 degrees of turn. A clockwise ring will be -360.
  A ring around a pole will be approximately 0 net degrees turn. If a ring crosses or has a point on
  a single pole then the sum will be -180 or 180. If a ring crosses both poles then the sum will be
  0."
  [ring]
  (let [courses (loop [courses (transient []) segments (:line-segments ring)]
                  (if (empty? segments)
                    (persistent! courses)
                    (let [ls (first segments)]
                      (recur (conj! courses (s/course ls)) (rest segments)))))
        ;; Add the first turn angle on again to complete the turn
        courses (conj courses (first courses))]
    (rotation-direction courses)))

(extend-protocol d/DerivedCalculator
  cmr.spatial.cartesian_ring.CartesianRing
  (calculate-derived
    [^CartesianRing ring]
    (if (.line_segments ring)
      ring
      (as-> ring ring
            (assoc ring :point-set (set (:points ring)))
            (assoc ring :line-segments (ring->line-segments ring))
            (assoc ring :mbr (ring->mbr ring))))))


(extend-protocol v/SpatialValidation
  cmr.spatial.cartesian_ring.CartesianRing
  (validate
    [ring]
    ;; Does no validation for now. CMR-1172 was filed to add this.
    ))