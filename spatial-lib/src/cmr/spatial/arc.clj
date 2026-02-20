(ns cmr.spatial.arc
  ;; cmr.spatial.arc is the first file in the project so here will be the only explination of the
  ;; following :refer-clojure call. This is needed to remove the "WARNING: abs already refers to..."
  ;; message seen when loading a file like this. The reason for this warning is that the spatial
  ;; math package does not currently use a third party library for abs like all the other functions
  ;; but instead uses the default version. Clojure has decided to warn us of a name change even
  ;; though the same math/abs version is the actual provider of the solution.
  (:refer-clojure :exclude [abs])
  (:require
   [cmr.common.dev.record-pretty-printer :as record-pretty-printer]
   [cmr.spatial.conversion :as c]
   [cmr.spatial.derived :as d]
   [cmr.spatial.math :refer [PI TAU abs acos approx= atan atan2 cos degrees double-approx= mid
                             mid-lon radians sin sq sqrt]]
   [cmr.spatial.mbr :as mbr]
   [cmr.spatial.point :as p]
   [cmr.spatial.vector :as v]
   [pjstadig.assertions :as pj]
   [primitive-math])
  (:import cmr.spatial.point.Point))

(primitive-math/use-primitive-operators)

(def ^:const ^double APPROXIMATION_DELTA
  "The delta in degreese to use when needing to compare things approximately."
  0.0000001)

(defrecord GreatCircle
  [plane-vector
   northernmost-point
   southernmost-point])

;; The arc contains derived information that is cached to prevent recalculating the same
;; information over and over again for the same arc. If desired this extra info could be put
;; in another record like ArcInfo
(defrecord Arc
  [;; The western most point of the arc
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
   mbr2])

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
  [^GreatCircle gc1 ^GreatCircle gc2]
  (v/parallel? (.plane_vector gc1) (.plane_vector gc2)))

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
          wp-lat (.lat west-point)
          ep-lat (.lat east-point)
          s (if (> wp-lat ep-lat) ep-lat wp-lat)
          n (if (> wp-lat ep-lat) wp-lat ep-lat)

          ;; If they're both on the antimeridian set west and east to the same value.
          both-antimeridian (and (= (abs w) 180.0) (= (abs e) 180.0))
          w (if both-antimeridian 180.0 w)
          e (if both-antimeridian 180.0 e)

          br (mbr/mbr w n e s)
          ^Point northernmost (.northernmost_point great-circle)
          ^Point southernmost (.southernmost_point great-circle)]

      (cond
          ;; Use the great circle northernmost and southernmost points to expand the bounding rectangle if necessary
        (mbr/covers-lon? :geodetic br (.lon northernmost))
        [(assoc br :north (.lat northernmost))]

        (mbr/covers-lon? :geodetic br (.lon southernmost))
        [(assoc br :south (.lat southernmost))]

        :else [br]))))

(defn- arc-from-ordered-points
  "Creates an arc from the given points that are already ordered west to east."
  [point1 point2 west-point east-point]
  (let [great-circle (great-circle west-point east-point)
        initial-course (p/course point1 point2)
        ending-course (mod (+ 180.0 (p/course point2 point1)) 360.0)
        [br1 br2] (bounding-rectangles west-point east-point great-circle initial-course ending-course)]
    (->Arc west-point east-point great-circle initial-course ending-course br1 br2)))

(defn arc
  [point1 point2]
  (pj/assert (not= point1 point2))
  (pj/assert (not (p/antipodal? point1 point2)))

  (if (neg? (p/compare-points point1 point2))
    (arc-from-ordered-points point1 point2 point1 point2)
    (arc-from-ordered-points point1 point2 point2 point1)))

(defn points->arcs
  "Takes a list of points and returns arcs connecting all the points"
  [points]
  (let [points (vec points)]
    (persistent!
     (reduce (fn [arcs ^long index]
               (conj! arcs (arc (nth points index) (nth points (inc index)))))
             (transient [])
             (range 0 (dec (count points)))))))

(defn ords->arc
  "Takes all arguments as coordinates for points, lon1, lat1, lon2, lat2, and creates an arc."
  [& ords]
  (apply arc (p/ords->points ords)))

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
      (crosses-north-pole? a)
      (crosses-south-pole? a)

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
  (when (or (some #(mbr/covers-lon? :geodetic % lon) (mbrs arc))
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
  [^Arc arc ^double lat]
  (let [mbr1 (.mbr1 arc)
        mbr2 (.mbr2 arc)]
    (when (or (mbr/covers-lat? mbr1 lat)
              (and mbr2 (mbr/covers-lat? mbr2 lat)))
      (let [^Point west-point (.west_point arc)
            ^Point east-point (.east_point arc)
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
                p1 (p/point (degrees lon3-1) lat lon3-1 lat3)
                p2 (p/point (degrees lon3-2) lat lon3-2 lat3)
                p1 (when (or (mbr/geodetic-covers-point? mbr1 p1)
                             (and mbr2 (mbr/geodetic-covers-point? mbr2 p1)))
                     p1)
                p2 (when (or (mbr/geodetic-covers-point? mbr1 p2)
                             (and mbr2 (mbr/geodetic-covers-point? mbr2 p2)))
                     p2)]
            (cond
              (and p1 p2) (if (= p1 p2) [p1] [p1 p2])
              p1 [p1]
              p2 [p2])))))))

(defn point-on-arc?
  "Returns true if the point is on the arc"
  [^Arc arc point]
  (let [west-point (.west_point arc)
        east-point (.east_point arc)
        mbr1 (.mbr1 arc)
        mbr2 (.mbr2 arc)]
    (and (or (mbr/geodetic-covers-point? mbr1 point)
             (and mbr2 (mbr/geodetic-covers-point? mbr2 point)))
         (or (= west-point point)
             (= east-point point)
             ;; If the arc is vertical and the point is in the mbr of the arc then it's on the arc
             (vertical? arc)

             ;; Find the point on the arc at the given longitude to see if it's the same point.
             (approx= point
                      (point-at-lon arc (:lon point))
                      APPROXIMATION_DELTA)))))
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
              _middle-dist (mid west-dist east-dist)]
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
                (and (mbr/geodetic-covers-point? lat-seg-mbr p)
                     (some #(mbr/geodetic-covers-point? % p) brs)))
              points))))

(defn intersects-lat-segment?
  "Returns true if the arc intersects the lat segment.  The latitude segment is definedat lat
  between the lon-west and lon-east"
  [arc lat lon-west lon-east]
  (seq (lat-segment-intersections arc lat lon-west lon-east)))

(def ^:const ^double ARC_INTERSECTION_EPS_RAD
  "Angular tolerance in radians"
  1.0e-10)

(def ^:const ^double ENDPOINT_EPS_T
  "Tolerance in param-space for endpoint classification"
  1.0e-9)

(defn- clamp
  ^double [^double x ^double lo ^double hi]
  (cond
    (< x lo) lo
    (> x hi) hi
    :else x))

(defn- unit-vec
  "Convert a Point (using cached radians) to a 3D unit vector as a double-array [x y z]."
  ^doubles [^Point p]
  (let [lon (double (.lon-rad p))
        lat (double (.lat-rad p))
        cos-lat (cos lat)]
    (double-array
      [(* cos-lat (cos lon))
       (* cos-lat (sin lon))
       (sin lat)])))

(defn- dot3
  ^double [^doubles a ^doubles b]
  (+ (* (aget a 0) (aget b 0))
     (* (aget a 1) (aget b 1))
     (* (aget a 2) (aget b 2))))

(defn- ang
  "Angular distance (radians) between two points on the unit sphere."
  ^double [^Point p1 ^Point p2]
  (let [^doubles v1 (unit-vec p1)
        ^doubles v2 (unit-vec p2)
        d (clamp (dot3 v1 v2) -1.0 1.0)]
    (acos (double d))))

(defn arc-contains-point?
  "True if candidate point lies on the finite great-circle arc between endpoints.
   Uses angle-sum test; assumes non-antipodal endpoints."
  ([^Arc arc ^Point p]
   (arc-contains-point? arc p ARC_INTERSECTION_EPS_RAD))
  ([^Arc arc ^Point p ^double eps-rad]
   (let [a (:west-point arc)
         b (:east-point arc)
         ab (ang a b)
         ap (ang a p)
         pb (ang p b)]
     ;; p is on the segment if ap + pb == ab (within tolerance)
     (<= (abs (- (+ ap pb) ab)) eps-rad))))

(defn arc-t
  "Parameter t in [0,1] locating p along the arc from west-point (t=0) to east-point (t=1).
   Only meaningful if arc-contains-point? is true."
  ^double [^Arc arc ^Point p]
  (let [a (:west-point arc)
        b (:east-point arc)
        ab (ang a b)]
    (if (zero? ab)
      0.0
      (/ (ang a p) ab))))

(defn endpoint-touch?
  "True if p lies very near an endpoint in parameter space."
  [^Arc arc ^Point p]
  (let [t (arc-t arc p)]
    (or (<= t ENDPOINT_EPS_T)
        (>= t (- 1.0 ENDPOINT_EPS_T)))))

(defn great-circle-equivalency-applicable?
  "Checks if special case for both arcs having the same great circle is applicable."
  [^Arc a1 ^Arc a2]
  (great-circle-equiv? (.great_circle a1) (.great_circle a2)))

(defn- point-within-arc-bounding-rectangles?
  "A helper function. Returns true if the point is within the bounding rectangles of the arcs"
  [p ^Arc a1 ^Arc a2]
  (let [a1-br1 (.mbr1 a1)
        a1-br2 (.mbr2 a1)
        a2-br1 (.mbr1 a2)
        a2-br2 (.mbr2 a2)]
    (and (or (mbr/geodetic-covers-point? a1-br1 p)
             (and a1-br2 (mbr/geodetic-covers-point? a1-br2 p)))
         (or (mbr/geodetic-covers-point? a2-br1 p)
             (and a2-br2 (mbr/geodetic-covers-point? a2-br2 p))))))

(defn great-circle-equivalency-arc-intersections
  "Special case arc intersections for both arcs having the same great circle"
  [a1 a2]
  (let [points (set (concat (arc->points a1) (arc->points a2)))]
    (->> points
         (filterv #(point-within-arc-bounding-rectangles? % a1 a2))
         (filterv #(and (arc-contains-point? a1 %)
                        (arc-contains-point? a2 %))))))

(defn- arc-mbrs-intersect?
  "Returns true if any of the arc mbrs intersect"
   [^Arc a1 ^Arc a2]
   (let [a1m1 (.mbr1 a1)
         a1m2 (.mbr2 a1)
         a2m1 (.mbr1 a2)
         a2m2 (.mbr2 a2)]
     (or (mbr/intersects-br? :geodetic a1m1 a2m1)
         (and a2m2 (mbr/intersects-br? :geodetic a1m1 a2m2))
         (and a1m2 (mbr/intersects-br? :geodetic a1m2 a2m1))
         (and a1m2 a2m2 (mbr/intersects-br? :geodetic a1m2 a2m2)))))

(defn default-arc-intersections [^Arc a1 ^Arc a2]
  (when (arc-mbrs-intersect? a1 a2)
    (let [pv1 (.plane_vector ^GreatCircle (.great_circle a1))
          pv2 (.plane_vector ^GreatCircle (.great_circle a2))
          ;; Compute the great circle intersection vector. This is the cross product of the vectors
          ;; defining the great circle planes.
          intersection-vector (v/cross-product pv1 pv2)
          intersection-point1 (c/vector->point intersection-vector)
          intersection-point2 (p/antipodal intersection-point1)
          candidates (cond-> []
                       true (conj intersection-point1)
                       true (conj intersection-point2))]
      (->> candidates
           ;; Return the intersection points that are covered by bounding rectangles from both arcs
           ;; Keep this MBR filter first as it is computationally cheap
           (filter #(point-within-arc-bounding-rectangles? % a1 a2))
           ;; Enforce finite-arc containment on both arcs to avoid false positives on "near misses"
           (filter #(and (arc-contains-point? a1 %)
                         (arc-contains-point? a2 %)))
           vec))))


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
