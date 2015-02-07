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
  (->CartesianRing (mapv p/with-cartesian-equality points) nil nil nil))

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

(defn ring->winding
  "Determines the winding of the cartesian polygon returning :clockwise or :counter-clockwise.
  Uses sum over the area under the edges solution as described here:
  http://stackoverflow.com/questions/1165647/how-to-determine-if-a-list-of-polygon-points-are-in-clockwise-order"
  [ring]
  (let [^double sum (->> (:line-segments ring)
                         (map (fn [{{^double x1 :lon ^double y1 :lat} :point1
                                    {^double x2 :lon ^double y2 :lat} :point2}]
                                (* (- x2 x1) (+ y2 y1))))
                         (apply clojure.core/+))]
    (if (>= sum 0.0)
      :clockwise
      :counter-clockwise)))

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
