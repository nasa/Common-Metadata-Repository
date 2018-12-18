(ns cmr.spatial.arc-line-segment-intersections
  "Provides intersection functions for finding the intersection of spherical arcs and cartesian segments"
  (:require [cmr.spatial.point :as p]
            [cmr.spatial.arc :as a]
            [cmr.spatial.mbr :as m]
            [cmr.common.util :as u]
            [cmr.spatial.line-segment :as s]
            [cmr.common.services.errors :as errors]
            [cmr.spatial.math :refer :all]
            [primitive-math]
            [pjstadig.assertions :as pj])
  (:import cmr.spatial.arc.Arc
           cmr.spatial.line_segment.LineSegment
           cmr.spatial.point.Point))
(primitive-math/use-primitive-operators)

(defn- vertical-arc-line-segment-intersections
  "Determines the intersection points of a vertical arc and a line segment"
  [ls arc]
  (let [;; convert the arc into a set of equivalent line segments.
        point1 (:west-point arc)
        point2 (:east-point arc)
        arc-segments (cond
                       ;; A vertical arc could cross a pole. It gets divided in half at the pole in that case.
                       (a/crosses-north-pole? arc)
                       [(s/line-segment point1 (p/point (:lon point1) 90))
                        (s/line-segment point2 (p/point (:lon point2) 90))]

                       (a/crosses-south-pole? arc)
                       [(s/line-segment point1 (p/point (:lon point1) -90))
                        (s/line-segment point2 (p/point (:lon point2) -90))]

                       (p/is-north-pole? point2)
                       ;; Create a vertical line segment ignoring the original point2 lon
                       [(s/line-segment point1 (p/point (:lon point1) 90))]

                       (p/is-north-pole? point1)
                       ;; Create a vertical line segment ignoring the original point1 lon
                       [(s/line-segment point2 (p/point (:lon point2) 90))]

                       (p/is-south-pole? point2)
                       ;; Create a vertical line segment ignoring the original point2 lon
                       [(s/line-segment point1 (p/point (:lon point1) -90))]

                       (p/is-south-pole? point1)
                       ;; Create a vertical line segment ignoring the original point1 lon
                       [(s/line-segment point2 (p/point (:lon point2) -90))]

                       :else
                       [(s/line-segment point1 point2)])]
    (keep #(s/intersection ls %) arc-segments)))

(defn line-segment-arc-intersections-with-densification
  "Performs the intersection between a line segment and the arc using densification of the line segment"
  [ls arc mbrs]

  (let [; Subselect the line segment so that we'll only find intersections within the mbrs of the arc.
        ;; Subselection can result in multiple line segments and points.
        {:keys [line-segments points]} (apply merge-with concat (map #(s/subselect ls %) mbrs))
        ;; We densify the line segments to see if they intersect the arcs
        densified-point-sets (mapv s/densify-line-segment line-segments)
        ;; Convert the lines of multiple points into separate arcs.
        densified-arcs (map #(apply a/arc %) (mapcat #(partition 2 1 %) densified-point-sets))]

    (concat
      ;; Return intersections of the densified arcs with the arc
      (mapcat #(a/intersections arc %) densified-arcs)
      ;; and any points that are on the original arc
      (filter #(a/point-on-arc? arc %) points))))


(defn line-segment-arc-intersections
  "Returns a list of the points where the line segment intersects the arc."
  [^LineSegment ls ^Arc arc]

  (let [ls-mbr (.mbr ls)
        arc-mbr1 (.mbr1 arc)
        arc-mbr2 (.mbr2 arc)
        mbr1-intersects (m/intersects-br? :geodetic ls-mbr arc-mbr1)
        mbr2-intersects (and arc-mbr2 (m/intersects-br? :geodetic ls-mbr arc-mbr2))
        arc-points (a/arc->points arc)
        ^Point ls-point1 (.point1 ls)
        ^Point ls-point2 (.point2 ls)]
    (when (or mbr1-intersects mbr2-intersects)
      (cond
        ;; Do both the Arc and Line Segment start or end on the same pole?
        (and (u/any-true? p/is-north-pole? arc-points)
             (or (p/is-north-pole? ls-point1) (p/is-north-pole? ls-point2)))
        [p/north-pole]

        (and (u/any-true? p/is-south-pole? arc-points)
             (or (p/is-south-pole? ls-point1) (p/is-south-pole? ls-point2)))
        [p/south-pole]

        (s/vertical? ls)
        ;; Treat as line segment as a vertical arc.
        (a/intersections arc (a/arc ls-point1 ls-point2))

        (s/horizontal? ls)
        ;; Use arc and latitude segment intersection implementation
        (let [lat (.lat ls-point1)
              lon1 (.lon ls-point1)
              lon2 (.lon ls-point2)
              west (if (< lon1 lon2) lon1 lon2)
              east (if (> lon1 lon2) lon1 lon2)]
          (a/lat-segment-intersections arc lat west east))

        (a/vertical? arc)
        (vertical-arc-line-segment-intersections ls arc)

        :else
        (line-segment-arc-intersections-with-densification
         ls arc

         ;; Compute the intersections of the intersecting mbrs. Smaller mbrs around the intersection
         ;; point will result in better bounding for newton's method.
         (concat
          (when mbr1-intersects (m/intersections ls-mbr arc-mbr1))
          (when mbr2-intersects (m/intersections ls-mbr arc-mbr2))))))))

(defprotocol ArcSegmentIntersects
  "Defines functions for intersecting with an arc or segments"
  (intersections-with-arc
    [line arc]
    "Returns the intersection points of the line with the arc.")
  (intersections-with-line-segment
    [line ls]
    "Returns the intersection points of the line with the line segment")
  (intersects-point?
    [line point]
    "Returns true if the point lies on the line"))

(defn intersections
  "Determines if line 1 and 2 intersect. A line can be an arc or a line segment."
  [line1 line2]

  (if (= (type line2) Arc)
    (intersections-with-arc line1 line2)
    (intersections-with-line-segment line1 line2)))

(extend-protocol ArcSegmentIntersects
  LineSegment
  (intersections-with-arc
    [ls arc]
    (line-segment-arc-intersections ls arc))
  (intersections-with-line-segment
    [ls1 ls2]
    (when-let [i (s/intersection ls1 ls2)]
      [i]))
  (intersects-point?
    [ls point]
    (s/point-on-segment? ls point))

  Arc
  (intersections-with-arc
    [arc1 arc2]
    (a/intersections arc1 arc2))
  (intersections-with-line-segment
    [arc ls]
    (line-segment-arc-intersections ls arc))
  (intersects-point?
    [arc point]
    (a/point-on-arc? arc point)))


(defn intersects?
  "Returns true if line1 intersects line2"
  [line1 line2]
  (seq (intersections line1 line2)))