(ns cmr.spatial.arc
  (:require [cmr.spatial.point :as p]
            [cmr.spatial.math :refer :all]
            [primitive-math]
            [pjstadig.assertions :as pj]
            [cmr.spatial.vector :as v]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.conversion :as c]
            [cmr.spatial.derived :as d]
            [cmr.common.util :as util]
            [cmr.common.dev.record-pretty-printer :as record-pretty-printer])
  (:import cmr.spatial.point.Point))
(primitive-math/use-primitive-operators)

(def APPROXIMATION_DELTA
  "The delta to use when needing to compare things approximately."
  0.0000001)

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

(record-pretty-printer/enable-record-pretty-printing
  GreatCircle
  Arc)

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
  (util/map-n (partial apply arc) 2 1 points))

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

(defn arc->points [a]
  [(:west-point a) (:east-point a)])

(defn vertical?
  "Returns true if the arc is vertical. Crossing a pole is considered vertical."
  [a]
  (let [{:keys [^Point west-point ^Point east-point]} a]
    (or
      ;; the longitudes are equal
      (double-approx= (.lon west-point) (.lon east-point) APPROXIMATION_DELTA)

      ;; It crosses a pole
      (or (crosses-north-pole? a) (crosses-south-pole? a))

      ;; one or the other point is a pole
      (and (p/is-pole? west-point) (not (p/is-pole? east-point)))
      (and (p/is-pole? east-point) (not (p/is-pole? west-point)))

      ;; both on antimeridian
      (and
        (double-approx= 180.0 (abs (.lon west-point)) APPROXIMATION_DELTA)
        (double-approx= 180.0 (abs (.lon east-point)) APPROXIMATION_DELTA)))))

(defn point-at-lon
  "Returns the point on the arc where it crosses a given longitude. Returns nil if the longitude
  is outside the bounds of the arc. Does not work for vertical arcs.
  Based on http://williams.best.vwh.net/avform.htm#Int"
  [^Arc arc lon]
  (pj/assert (not (vertical? arc)))
  (when (or (some #(mbr/covers-lon? % lon) (mbrs arc))
            (crosses-south-pole? arc)
            (crosses-north-pole? arc))
    (let [{:keys [^Point west-point ^Point east-point]} arc
          lon-rad (radians lon)
          lon1 (.lon-rad west-point)
          lon2 (.lon-rad east-point)
          lat1 (.lat-rad west-point)
          lat2 (.lat-rad east-point)
          cos-lat1 (cos lat1)
          cos-lat2 (cos lat2)
          ;; From http://williams.best.vwh.net/avform.htm#Int
          ; top = sin(lat1) * cos(lat2) * sin(mid - lon2) - sin(lat2) * cos(lat1) * sin(mid - lon1)
          ; bottom = cos(lat1) * cos(lat2) * sin(lon1 - lon2)
          ; lat = atan(top/bottom)
          top (- (* (sin lat1)
                    cos-lat2
                    (sin (- lon-rad lon2)))
                 (* (sin lat2)
                    cos-lat1
                    (sin (- lon-rad lon1))))
          bottom (* cos-lat1 cos-lat2 (sin (- lon1 lon2)))
          lat-rad (atan (/ top bottom))]
      (p/point (degrees lon-rad) (degrees lat-rad) lon-rad lat-rad))))

(defn points-at-lat
  "Returns the points where the arc crosses at a given latitude. Returns nil if the arc
  does not cross that lat. Based on http://williams.best.vwh.net/avform.htm#Par"
  [arc ^double lat]
  (when (some #(mbr/covers-lat? % lat) (mbrs arc))
    (let [{:keys [^Point west-point ^Point east-point]} arc
          lat3 (radians lat) ; lat3 is just the latitude argument in radians
          lon1 (.lon-rad west-point)
          lon2 (.lon-rad east-point)
          lat1 (.lat-rad west-point)
          lat2 (.lat-rad east-point)
          lon12 (- lon1 lon2)
          sin-lon12 (sin lon12)
          cos-lon12 (cos lon12)
          sin-lat1 (sin lat1)
          cos-lat1 (cos lat1)
          sin-lat2 (sin lat2)
          cos-lat2 (cos lat2)
          sin-lat3 (sin lat3)
          cos-lat3 (cos lat3)

          ;;  A = sin(lat1) * cos(lat2) * cos(lat3) * sin(l12)
          A (* sin-lat1 cos-lat2 cos-lat3 sin-lon12)
          ;;  B = sin(lat1)*cos(lat2)*cos(lat3)*cos(l12) - cos(lat1)*sin(lat2)*cos(lat3)
          B (- (* sin-lat1 cos-lat2 cos-lat3 cos-lon12)
               (* cos-lat1 sin-lat2 cos-lat3))
          ;;  C = cos(lat1)*cos(lat2)*sin(lat3)*sin(l12)
          C (* cos-lat1 cos-lat2 sin-lat3 sin-lon12)
          h (sqrt (+ (sq A) (sq B)))]

      (when (<= (abs C) h)
        (let [lon-rad (atan2 B A)
              ;;   dlon = acos(C/sqrt(A^2+B^2))
              dlon (acos (/ C h))
              ;;   lon3_1=mod(lon1+dlon+lon+pi, 2*pi)-pi
              lon3-1 (- ^double (mod (+ lon1 dlon lon-rad PI)
                                     TAU)
                        PI)
              ;;   lon3_2=mod(lon1-dlon+lon+pi, 2*pi)-pi
              lon3-2 (- ^double (mod (+ (- lon1 dlon) lon-rad PI)
                                     TAU)
                        PI)
              points [(p/point (degrees lon3-1) lat lon3-1 lat3)
                      (p/point (degrees lon3-2) lat lon3-2 lat3)]]
          (->> points
               (filterv (fn [p]
                          (some #(mbr/covers-point? :geodetic % p)
                                (mbrs arc))))
               set))))))

(defn point-on-arc?
  "Returns true if the point is on the arc"
  [arc point]
  (let [{:keys [west-point east-point]} arc]
    (and (some #(mbr/covers-point? :geodetic % point) (mbrs arc))
         (or (= west-point point)
             (= east-point point)
             ;; If the arc is vertical and the point is in the mbr of the arc then it's on the arc
             (vertical? arc)

             ;; Find the point on the arc at the given longitude to see if it's the same point.
             (approx= point
                      (point-at-lon arc (:lon point))
                      APPROXIMATION_DELTA)))))

(comment

  (def a1 (ords->arc -45 30 0 0))
  (def a2 (ords->arc -45 0 0 30))

  (midpoint a1)
  (midpoint a2)

  (lat-segment-intersections
    a1 16.175960878620344 -50 0)

  (point-at-lon a1 -20.772845524037166)

  (def ip (p/point -20.772845524037166,16.150056475830688))
  (def aip (p/point -22.5,17.351921757240685))

  (def ^double offset (p/angular-distance ip aip))
  (def ^double total-dist (p/angular-distance (:west-point a1) (:east-point a1)))
  (def ^double percent-of-total (*(/ ^double offset ^double total-dist)))

  ;; An arc farther shorter than a2 but along the same cartesian line
  (def a3 (ords->arc -30 10 -15 20))

  (intersections a3 a1)
  (def ip-a3 (p/point -20.945413480816008 16.271461041452543))

  (def ^double offset-a3 (p/angular-distance ip-a3 aip))
  (def ^double percent-of-total-a3 (* 100.0 (/ ^double offset-a3 ^double total-dist)))




)


(defn midpoint
  "Finds the midpoint of the arc."
  [^Arc arc]
  (let [{:keys [^Point west-point ^Point east-point]} arc]
    (if (vertical? arc)
      (cond
        ;; Vertical across north pole
        (crosses-north-pole? arc)
        (let [west-dist (- 90.0 (.lat west-point))
              east-dist (- 90.0 (.lat east-point))
              middle-dist (mid west-dist east-dist)]
          (cond
            (= west-dist east-dist) p/north-pole
            (< west-dist east-dist) (p/point (.lon east-point)
                                             (- 90.0 (/ (- east-dist west-dist) 2.0)))
            :else (p/point (.lon west-point)
                           (- 90.0 (/ (- west-dist east-dist) 2.0)))))

        ;; Vertical across south pole
        (crosses-south-pole? arc)
        (let [west-dist (- (.lat west-point) -90.0)
              east-dist (- (.lat east-point) -90.0)]
          (cond
            (= west-dist east-dist) p/south-pole
            (< west-dist east-dist) (p/point (.lon east-point)
                                             (+ -90.0 (/ (- east-dist west-dist) 2.0)))
            :else (p/point (.lon west-point)
                           (+ -90.0 (/ (- west-dist east-dist) 2.0)))))

        ;; Vertical arc not crossing a pole
        :else
        (let [not-pole-point (if (p/is-pole? west-point) east-point west-point)]
          (p/point (.lon not-pole-point) (mid (.lat west-point) (.lat east-point)))))

      ;; Not a vertical arc
      (point-at-lon arc (mid-lon (.lon west-point) (.lon east-point))))))

(defn- points-within-arc-bounding-rectangles
  "A helper function. Returns the points that are within the bounding rectangles of the arcs"
  [points ^Arc a1 ^Arc a2]
  (let [a1-br1 (.mbr1 a1)
        a1-br2 (.mbr2 a1)
        a2-br1 (.mbr1 a2)
        a2-br2 (.mbr2 a2)]
    (filter (fn [p] (and (or (mbr/covers-point? :geodetic a1-br1 p)
                             (and a1-br2 (mbr/covers-point? :geodetic a1-br2 p)))
                         (or (mbr/covers-point? :geodetic a2-br1 p)
                             (and a2-br2 (mbr/covers-point? :geodetic a2-br2 p)))))
            points)))

(defn lat-segment-intersections
  "Returns the points where an arc intersects the latitude segment. The latitude segment is defined
  at lat between the lon-west and lon-east"
  [arc lat ^double lon-west ^double lon-east]
  (pj/assert (< lon-west lon-east))
  ;; Find the points where the arc crosses that latitude (if any)
  (when-let [points (points-at-lat arc lat)]
    ;; See if those points are within the lon-west and east
    (let [lat-seg-mbr (mbr/mbr lon-west lat lon-east lat)
          brs (mbrs arc)]
      (filter (fn [p]
                (and (some #(mbr/covers-point? :geodetic % p) brs)
                     (mbr/covers-point? :geodetic lat-seg-mbr p)))
              points))))

(defn intersects-lat-segment?
  "Returns true if the arc intersects the lat segment.  The latitude segment is definedat lat
  between the lon-west and lon-east"
  [arc lat lon-west lon-east]
  (seq (lat-segment-intersections arc lat lon-west lon-east)))

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

;; Performance enhancement: Add a bounding rectangle's intersects check first.
;; Actually that might not help anything. When we're searching in elastic we'll only find those
;; items where the rings bounding rectangles intersect. Still might be worth it to check with arcs though.

(defn intersections
  "Returns a list of the points where the two arcs intersect."
  [a1 a2]
  (if (great-circle-equivalency-applicable? a1 a2)
    (great-circle-equivalency-arc-intersections a1 a2)
    (default-arc-intersections a1 a2)))

(defn intersects?
  "Returns true if a1 intersects a2"
  [a1 a2]
  (seq (intersections a1 a2)))


(extend-protocol d/DerivedCalculator
  cmr.spatial.arc.Arc
  (calculate-derived ^Arc [^Arc a] a))
