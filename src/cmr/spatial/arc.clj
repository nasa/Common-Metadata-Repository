(ns cmr.spatial.arc
  (:require [cmr.spatial.point :as p]
            [cmr.spatial.math :refer :all]
            [primitive-math]
            [pjstadig.assertions :as pj]
            [cmr.spatial.vector :as v]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.conversion :as c]
            [cmr.common.util :as util])
  (:import cmr.spatial.point.Point))
(primitive-math/use-primitive-operators)

(defrecord GreatCircle
  [
    plane-vector

    northernmost-point

    southernmost-point
   ])


;; The arc contains derived information that is cached to prevent recalculating the same
;; information over and over again for the same arc. If desired this extra info could be put
;; in another record like ArcInfo
(defrecord Arc
  [
   ;; The western most point of the arc
   ^Point west-point
   ;; The eastern most point of the arc.
   ^Point east-point

   ;; A representation of the great circle the arc lies on.
   ^GreatCircle great-circle

   ;; The initial course when following the arc. This is derived from the order of the points passed
   ;; into the arc constructor. It won't necessarily be from west to east or east to west.
   ^double initial-course

   ;; The ending course when following the arc.
   ^double ending-course

   ;; 1 or 2 bounding rectangles defining the MBR of the arc.
   mbr1
   mbr2
   ])

(defn- great-circle
  "Creates great circle information from a west point and east point."
  [west-point east-point]
  (let [plane-vector (v/normalize (c/lon-lat-cross-product west-point east-point))
        ^Point pv-point (c/vector->point plane-vector)
        plon (.lon pv-point)
        plat (.lat pv-point)

        ;; Calculate the great circle northernmost and southernmost points.
        ;; We use the knowledge that the great circle that intersects the plane vector point
        ;; and the north pole will intersect this great circle's northernmost and southernmost
        ;; latitudes
        north-lon plon
        north-lat (- 90.0 (abs plat))

        south-lon (if (> (+ plon 180.0) 180.0)
                    (- plon 180.0)
                    (+ plon 180.0))
        south-lat (- (abs plat) 90.0)]
    (->GreatCircle plane-vector
                   (p/point north-lon north-lat)
                   (p/point south-lon south-lat))))

(defn- great-circle-equiv?
  "Returns true if two great circles are equivalent. Two great circles are equivalent if there
  vectors are parallel."
  [gc1 gc2]
  (v/parallel? (:plane-vector gc1) (:plane-vector gc2)))

(defn crosses-north-pole?
  "Returns true if the arc crosses the northpole"
  [^Arc a]
  (and (= 0.0 (.initial_course a))
       (= 180.0 (.ending_course a))))

(defn crosses-south-pole?
  "Returns true if the arc crosses the south pole"
  [^Arc a]
  (and (= 180.0 (.initial_course a))
       (= 0.0 (.ending_course a))))

(defn mbrs
  "Returns the minimum bounding rectangles for an arc"
  [^Arc a]
  (let [mbr1 (.mbr1 a)
        mbr2 (.mbr2 a)]
    (if mbr2
      [mbr1 mbr2]
      [mbr1])))

(defn crosses-antimeridian?
  "Returns true if the arc crosses the antimeridian."
  [^Arc a]
  (let [mbr1 (.mbr1 a)
        mbr2 (.mbr2 a)]
    (or (mbr/crosses-antimeridian? mbr1)
        (and mbr2 (mbr/crosses-antimeridian? mbr2)))))

(defn- bounding-rectangles
  "Calculates the bounding rectangles for an arc"
  [^Point west-point ^Point east-point ^GreatCircle great-circle initial-course ending-course]
    (cond
      ;; Crosses North pole?
      (and (= 0.0 initial-course) (= 180.0 ending-course))
      ;; Results in two mbrs that have no width going from lon up to north pole
      (let [br1 (mbr/mbr (.lon west-point) 90.0 (.lon west-point) (.lat west-point))
            br2 (mbr/mbr (.lon east-point) 90.0 (.lon east-point) (.lat east-point))]
        [br1 br2])

      ;; Crosses South pole?
      (and (= 180.0 initial-course) (= 0.0 ending-course))
      ;; Results in two mbrs that have no width going from lon down to south pole
      (let [br1 (mbr/mbr (.lon west-point) (.lat west-point) (.lon west-point) -90.0)
            br2 (mbr/mbr (.lon east-point) (.lat east-point) (.lon east-point) -90.0)]
        [br1 br2])

      :else
      (let [w (.lon west-point)
            e (.lon east-point)

            ;; If one point is at a pole the west and east longitudes should match
            w (if (or (p/is-north-pole? west-point) (p/is-south-pole? west-point))
                e
                w)
            e (if (or (p/is-north-pole? east-point) (p/is-south-pole? east-point))
                w
                e)
            ;; Choose north and south extents
            [s n] (if (> (.lat west-point) (.lat east-point))
                    [(.lat east-point) (.lat west-point)]
                    [(.lat west-point) (.lat east-point)])

            ;; If they're both on the antimeridian set west and east to the same value.
            [w e] (if (and (= (abs w) 180.0) (= (abs e) 180.0))
                    [180.0 180.0]
                    [w e])

            br (mbr/mbr w n e s)
            ^Point northernmost (.northernmost_point great-circle)
            ^Point southernmost (.southernmost_point great-circle)]

        (cond
          ;; Use the great circle northernmost and southernmost points to expand the bounding rectangle if necessary
          (mbr/covers-lon? br (.lon northernmost))
          [(assoc br :north (.lat northernmost))]

          (mbr/covers-lon? br (.lon southernmost))
          [(assoc br :south (.lat southernmost))]

          :else [br]))))

(defn arc
  [point1 point2]
  (pj/assert (not= point1 point2))
  (pj/assert (not (p/antipodal? point1 point2)))

  (let [[west-point east-point] (p/order-points point1 point2)
        great-circle (great-circle west-point east-point)
        initial-course (p/course point1 point2)
        ending-course (mod (+ 180.0 (p/course point2 point1)) 360.0)
        [br1 br2] (bounding-rectangles west-point east-point great-circle initial-course ending-course)]
    (->Arc west-point east-point great-circle initial-course ending-course br1 br2)))

(defn points->arcs
  "Takes a list of points and returns arcs connecting all the points"
  [points]
  (util/map-n arc 2 1 points))

(defn ords->arc
  "Takes all arguments as coordinates for points, lon1, lat1, lon2, lat2, and creates an arc."
  [& ords]
  (apply arc (apply p/ords->points ords)))

(defn arc->ords
  "Returns a list of the arc ordinates lon1, lat1, lon2, lat2"
  [a]
  (let [{{lon1 :lon lat1 :lat} :west-point
         {lon2 :lon lat2 :lat} :east-point} a]
    [lon1, lat1, lon2, lat2]))

(defn antipodal
  "Returns the antipodal arc to the arc passed in"
  [a]
  (let [{:keys [west-point east-point]} a]
    (arc (p/antipodal west-point) (p/antipodal east-point))))

(defn- arc->points [a]
  [(:west-point a) (:east-point a)])

(defn- points-within-arc-bounding-rectangles
  "A helper function. Returns the points that are within the bounding rectangles of the arcs"
  [points ^Arc a1 ^Arc a2]
  (let [a1-br1 (.mbr1 a1)
        a1-br2 (.mbr2 a1)
        a2-br1 (.mbr1 a2)
        a2-br2 (.mbr2 a2)]
    (filter (fn [p] (and (or (mbr/covers-point? a1-br1 p)
                             (and a1-br2 (mbr/covers-point? a1-br2 p)))
                         (or (mbr/covers-point? a2-br1 p)
                             (and a2-br2 (mbr/covers-point? a2-br2 p)))))
            points)))

(defn great-circle-equivalency-applicable?
  "Checks if special case for both arcs having the same great circle is applicable."
  [^Arc a1 ^Arc a2]
  (great-circle-equiv? (.great_circle a1) (.great_circle a2)))

(defn great-circle-equivalency-arc-intersections
  "Special case arc intersections for both arcs having the same great circle"
  [a1 a2]
  (let [points (set (concat (arc->points a1) (arc->points a2)))]
    (points-within-arc-bounding-rectangles
      points a1 a2)))

(defn default-arc-intersections [^Arc a1 ^Arc a2]
  (let [pv1 (.plane_vector ^GreatCircle (.great_circle a1))
        pv2 (.plane_vector ^GreatCircle (.great_circle a2))
        ;; Compute the great circle intersection vector. This is the cross product of the vectors
        ;; defining the great circle planes.
        intersection-vector (v/cross-product pv1 pv2)
        intersection-point1 (c/vector->point intersection-vector)
        intersection-point2 (p/antipodal intersection-point1)]
    ;; Return the intersection points that are covered by bounding rectangles from both arcs
    (points-within-arc-bounding-rectangles
      [intersection-point1 intersection-point2]
      a1 a2)))


;; TODO performance enhancement. Add a bounding rectangle's intersects check first.
;; Actually that might not help anything. When we're searching in elastic we'll only find those
;; items where the rings bounding rectangles intersect. Still might be worth it to check with arcs though.

(defn intersections
  "Returns a list of the points where the two arcs intersect."
  [a1 a2]
  (if (great-circle-equivalency-applicable? a1 a2)
    (great-circle-equivalency-arc-intersections a1 a2)
    (default-arc-intersections a1 a2)))
