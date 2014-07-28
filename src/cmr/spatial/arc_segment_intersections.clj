(ns cmr.spatial.arc-segment-intersections
  "Provides intersection functions for finding the intersection of spherical arcs and cartesian segments"
  (:require [cmr.spatial.point :as p]
            [cmr.spatial.arc :as a]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.segment :as s]
            [cmr.common.services.errors :as errors]
            [cmr.spatial.math :refer :all]
            [primitive-math]
            [pjstadig.assertions :as pj])
  (:import cmr.spatial.arc.Arc
           cmr.spatial.segment.LineSegment
           cmr.spatial.point.Point))
(primitive-math/use-primitive-operators)

(defn intersection-with-densification
  "Performs the intersection between a line segment and the arc using densification of the line segment"
  [ls arc mbrs]
  (let [line-segments (filter identity (map (partial s/subselect ls) mbrs))
        lines (mapv s/line-segment->line line-segments)
        arcs (map (partial apply a/arc) (mapcat #(partition 2 1 (:points %)) lines))]
    (mapcat (partial a/intersections arc) arcs)))

(defn- vertical-arc-line-segment-intersections
  "Determines the intersection points of a vertical arc and a line segment"
  [ls arc]
  (let [;; convert the arc into a set of equivalent line segments.
        point1 (:west-point arc)
        point2 (:east-point arc)
        arc-segments (cond
                       ;; A vertical arc could cross a pole. It gets divided in half at the pole in that case.
                       (a/crosses-north-pole? arc)
                       [(s/line-segment point1 p/north-pole)
                        (s/line-segment point2 p/north-pole)]

                       (a/crosses-south-pole? arc)
                       [(s/line-segment point1 p/south-pole)
                        (s/line-segment point2 p/south-pole)]

                       :else
                       [(s/line-segment point1 point2)])]
    (filter identity (map (partial s/intersection ls) arc-segments))))

(defn intersections
  "Returns a list of the points where the line segment intersects the arc."
  [ls arc]

  (let [ls-mbr (:mbr ls)
        arc-mbrs (mapcat m/split-across-antimeridian (a/mbrs arc))
        intersecting-mbrs (seq (filter (partial m/intersects-br? ls-mbr)
                                       arc-mbrs))]
    (when intersecting-mbrs
      (cond

        (s/vertical? ls)
        ;; Treat as line segment as a vertical arc.
        (a/intersections arc (a/arc (:point1 ls) (:point2 ls)))


        (s/horizontal? ls)
        ;; Use arc and latitude segment intersection implementation
        (let [lat (-> ls :point1 :lat)
              [west east] (p/order-longitudes (get-in ls [:point1 :lon]) (get-in ls [:point2 :lon]))]
          (a/lat-segment-intersections arc lat west east))

        (a/vertical? arc)
        (vertical-arc-line-segment-intersections ls arc)

        :else
        (intersection-with-densification
          ls arc
          ;; Compute the intersections of the intersecting mbrs. Smaller mbrs around the intersection
          ;; point will result in better bounding for newton's method.
          (mapcat (partial m/intersections ls-mbr) intersecting-mbrs))))))


