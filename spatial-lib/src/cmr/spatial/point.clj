(ns cmr.spatial.point
  "This namespace defines a Point type along with functions for working with points."
  (:require [cmr.spatial.math :refer :all]
            [primitive-math]
            [clojure.pprint]
            [pjstadig.assertions :as pj]
            [cmr.spatial.derived :as d]
            [cmr.common.util :as util]
            [cmr.spatial.validation :as v]
            [cmr.spatial.messages :as msg]))

(primitive-math/use-primitive-operators)

(def ^:const INTERSECTION_POINT_PRECISION
  "The precision in degrees to use when generating intersection points. We round the points because
  in some cases the same point will be found multiple times with vary slight variations. Rounding it
  within a set eliminates the duplication. This is important for determining if a point is inside a
  ring which relies on knowing exactly how many times an arc between the test point and an external
  point crosses over the arcs of the ring."
  5)

(declare is-north-pole?
         is-south-pole?
         on-antimeridian?
         north-pole
         south-pole)

(def ^:const ^:private ^int PRIME 31)

(def ^:const ^:private ^int NORTH_POLE_HASH
  "Precomputed hash code for points on or very near the north pole."
  (let [lon-hash (+ PRIME (.hashCode (Double. 0.0)))]
    (int (+ (* PRIME lon-hash) (.hashCode (Double. 90.0))))))

(def ^:const ^:private ^int SOUTH_POLE_HASH
  "Precomputed hash code for points on or very near the south pole."
  (let [lon-hash (+ PRIME (.hashCode (Double. 0.0)))]
    (int (+ (* PRIME lon-hash) (.hashCode (Double. -90.0))))))

(def ^:const ^:private ^:int INITIAL_AM_HASH
  "Precomputed hash seed for longitude portion of antimeridian points"
  (* PRIME (+ PRIME ^int (.hashCode (Double. 180.0)))))

;; Point type definition
;; It is a Type and not a record because it has some special rules that don't follow normal
;; equality semantics. This implements enough functions and protocols to simulate a record.
;; Also Point maintains cached versions of lon and lat in radians. It keeps those values consistent
;; when creating a new point with a different lon or lat in assoc.
(deftype Point
  [
   ^double lon
   ^double lat
   ^double lon-rad
   ^double lat-rad

   ;; This is a flag that changes how the point determines equality semantics. Geodetic spatial
   ;; representation exists on a spherical earth. Cartesian is a flat plane limited to -180 to 180
   ;; on the X axis and -90 to 90 on the Y axis. Points on a sphere are equal at the poles or on
   ;; the antimeridian with values -180 or 180. Defaults to true.
   geodetic-equality

   ;; This field is holding options when debugging points so they can be displayed in a visualization
   ;; with labels etc. It will typically be set to null during normal operations. It is excluded
   ;; from hashCode and equals.
   ^clojure.lang.Associative options]

  Object
  (hashCode
    [this]
    (let [lon-hash (.hashCode (Double. lon))
          lat-hash (.hashCode (Double. lat))
          combined-hash (+ (* PRIME (+ PRIME lon-hash)) lat-hash)]
      (if geodetic-equality
        (cond
          (is-north-pole? this) NORTH_POLE_HASH
          (is-south-pole? this) SOUTH_POLE_HASH
          (= (abs lon) 180.0) (+ INITIAL_AM_HASH lat-hash)
          :else combined-hash)
        combined-hash)))
  (equals
    [this other]
    (or (identical? this other)
        (and (not (nil? other))
             (= (class other) (class this))
             (let [^Point other other]
               (and (= geodetic-equality (.geodetic_equality other))
                    (or
                      ;; Geodetic special cases
                      (and geodetic-equality
                           (or
                             ;; Any longitude value at north pole is considered equivalent
                             (and (is-north-pole? this) (is-north-pole? other))

                             ;; Any longitude value at south pole is considered equivalent
                             (and (is-south-pole? this) (is-south-pole? other))

                             ;; -180 and 180 are considered equivalent longitudes
                             (and (on-antimeridian? this)
                                  (on-antimeridian? other)
                                  (= lat (.lat other)))))
                      ;; Normal geodetic and cartesian equality
                      (and (= lon (.lon other))
                           (= lat (.lat other)))))))))
  (toString
    [this]
    (pr-str this))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;; Protocols implemented to simulate a clojure Record.

  clojure.lang.IPersistentMap
  (assoc
    [_ k v]
    (cond
      (= k :lon) (Point. v lat (radians v) lat-rad geodetic-equality options)
      (= k :lon-rad) (Point. (degrees v) lat v lat-rad geodetic-equality options)
      (= k :lat) (Point. lon v lon-rad (radians v) geodetic-equality options)
      (= k :lat-rad) (Point. lon (degrees v) lon-rad v geodetic-equality options)
      (= k :geodetic-equality) (Point. lon lat lon-rad lat-rad v options)
      (= k :options) (Point. lon lat lon-rad lat-rad geodetic-equality v)
      :else (throw (Exception. (str "Unknown point key " k)))))
  (assocEx
    [this k v]
    (.assoc this k v))
  (without
    [_ k]
    (throw (Exception. "Dissassociation not supported on a point")))

  clojure.lang.Associative
  (containsKey
    [this k]
    (nil? (.valAt this k)))
  (entryAt
    [this k]
    (clojure.lang.MapEntry. k (.valAt this k)))

  clojure.lang.IPersistentCollection
  (equiv [this o]
         (and (isa? (class o) Point)
              (.equals this o)))
  ;; Don't know what I am doing, put the following three functions here to deal with CI build error:
  ;; java.lang.AbstractMethodError: Method cmr/spatial/point/Point.count()I is abstract
  (count [this] 100)
  (cons [this _o] nil)
  (empty [this] nil)

  clojure.lang.Seqable
  (seq
    [_]
    (seq {:lon lon :lat lat :lon-rad lon-rad :lat-rad lat-rad :geodetic-equality geodetic-equality}))

  clojure.lang.ILookup
  (valAt
    [_ k]
    (case k
      :lon lon
      :lon-rad lon-rad
      :lat lat
      :lat-rad lat-rad
      :options options
      :geodetic-equality geodetic-equality
      nil))
  (valAt [_ k not-found]
         (case k
           :lon lon
           :lon-rad lon-rad
           :lat lat
           :lat-rad lat-rad
           :options options
           :geodetic-equality geodetic-equality
           not-found)))

(defn print-point
  "Prints the point in a way that it can be copy and pasted for testing"
  [^Point p ^java.io.Writer writer]
  (.write writer (str "#=" (apply list (into ['cmr.spatial.point/point]
                                             [(.lon p) (.lat p) (.geodetic_equality p)])))))

;; These define Clojure built in multimethods for point so it can be printed easily
(defmethod print-method Point [p writer]
  (print-point p writer))

(defmethod print-dup Point [p writer]
  (print-point p writer))

(.addMethod ^clojure.lang.MultiFn clojure.pprint/simple-dispatch
            Point
            (fn [p]
              (print-point p *out*)))

(defn point
  "Creates a new point from longitude and latitude and optionally pre-computed radian values of lon
  and lat."
  (^Point [lon lat]
          (point lon lat (radians lon) (radians lat)))
  (^Point [lon lat geodetic-equality]
          (point lon lat (radians lon) (radians lat) geodetic-equality))
  (^Point [lon lat lon-rad lat-rad]
          (point lon lat (radians lon) (radians lat) true))
  (^Point [lon lat lon-rad lat-rad geodetic-equality]
          (pj/assert (double-approx= (radians lon) ^double lon-rad))
          (pj/assert (double-approx= (radians lat) ^double lat-rad))

          (Point. lon
                  lat
                  lon-rad
                  lat-rad
                  geodetic-equality
                  nil)))

(def north-pole
  (point 0.0 90.0))

(def south-pole
  (point 0.0 -90.0))

(defn with-cartesian-equality
  "Returns an equivalent point with cartesian equality"
  [^Point p]
  (if (.geodetic_equality p)
    (assoc p :geodetic-equality false)
    p))

(defn with-geodetic-equality
  "Returns an equivalent point with geodetic-equality equality"
  [^Point p]
  (if (.geodetic_equality p)
    p
    (assoc p :geodetic-equality true)))

(defn with-equality
  "Returns an equivalent point with the equality semantics of the given coordinate system."
  [coordinate-system p]
  (if (= coordinate-system :geodetic)
    (with-geodetic-equality p)
    (with-cartesian-equality p)))

(defn compare-longitudes
  "Compares the longitudes from west to east such that traveling east crosses at most 180 degrees.
   Returns information following the same semantics as clojure.core/compare"
  ^long [^double l1 ^double l2]
  (let [^double mod (mod (- l1 l2) 360)]
    (cond
      ;; use natural ordering in this case
      (= mod 180.0) (compare l1 l2)
      (= mod 0.0) (if (= l1 180.0) LESS_THAN GREATER_THAN)
      (< mod 180.0) GREATER_THAN
      :else LESS_THAN)))

(defn compare-points
  "Compares the points from west to east using the same rules as order longitudes. If points are the
   same longitude ordered south to north. Returns information following the same semantics as
   clojure.core/compare"
  ^long [^Point p1 ^Point p2]
  (let [lon1 (.lon p1)
        lat1 (.lat p1)
        lon2 (.lon p2)
        lat2 (.lat p2)]
    (if (= lon1 lon2)
      ;; Order by latitudes
      (compare lat1 lat2)
      ;; order by longitudes
      (compare-longitudes lon1 lon2))))

(defn round-point
  "Rounds the point the given number of decimal places"
  [num-places ^Point p]
  (let [lon (round num-places (.lon p))
        lat (round num-places (.lat p))]
    (point lon lat (.geodetic_equality p))))

(defn ords->points
  "Takes pairs of numbers and returns a sequence of points.

  user=> (ords->points [1 2 3 4])
  ((cmr-spatial.point/point 1.0 2.0) (cmr-spatial.point/point 3.0 4.0))"
  ([ords]
   (ords->points ords true))
  ([ords geodetic-equality]
   (let [ords (vec ords)]
     (persistent!
      (reduce (fn [points ^long index]
                (conj! points (point (nth ords index) (nth ords (inc index)) geodetic-equality)))
              (transient [])
              (range 0 (count ords) 2))))))

(defn points->ords
  "Takes points and converts them to a list of numbers lon1, lat1, lon2, lat2, ..."
  [points]
  (vec (mapcat #(vector (:lon %) (:lat %)) points)))

(defn antipodal
  "Returns the point antipodal to the point."
  [^Point p]
  (point (antipodal-lon (.lon p))
         (* -1.0 (.lat p))
         (.geodetic_equality p)))

(defn antipodal?
  "Returns true if the points are antipodal to one another."
  [^Point p1 ^Point p2]
  (cond
    (is-north-pole? p1) (is-south-pole? p2)
    (is-south-pole? p1) (is-north-pole? p2)
    :else
    (and
      (= (* -1.0 (.lat p1)) (.lat p2))
      (= (antipodal-lon (.lon p1)) (.lon p2)))))

(defn is-north-pole? [^Point p]
  (double-approx= (.lat p) 90.0 0.0000001))

(defn is-south-pole? [^Point p]
  (double-approx= (.lat p) -90.0 0.0000001))

(defn is-pole? [p]
  (or (is-north-pole? p)
      (is-south-pole? p)))

(defn on-antimeridian? [^Point p]
  (or (= (abs (.lon p)) 180.0)
      (is-north-pole? p)
      (is-south-pole? p)))

(defmacro sin-sq
  "Helper for computing angular distance"
  [v1 v2]
  `(sq (sin (/ (- ~v1 ~v2) 2.0))))

(defn angular-distance
  "Returns the angular distance between the points in radians
  From: http://williams.best.vwh.net/avform.htm#Dist"
  ^double [^Point p1 ^Point p2]
  (pj/assert (not (antipodal? p1 p2)))
  (let [lon1 (.lon_rad p1)
        lat1 (.lat_rad p1)
        lon2 (.lon_rad p2)
        lat2 (.lat_rad p2)
        part1 (sin-sq lat1 lat2)
        part2 (* (cos lat1) (cos lat2) (sin-sq lon1 lon2))]
    (* 2.0 (asin (sqrt (+ part1 part2))))))

(defn distance
  ^double [^Point p1 ^Point p2]
  (* (angular-distance p1 p2) EARTH_RADIUS_METERS))

(defn course
  "Returns the initial bearing between two points. The bearing starts at 0 pointing towards the north
  pole and increases clockwise. 180 points to the south pole. 360 points the same direction as 0.
  Algorithm from http://www.movable-type.co.uk/scripts/latlong.html Bearing calculation"
  ^double [^Point p1 ^Point p2]
  (let [due-north 0.0
        due-south 180.0
        lon-deg1 (.lon p1)
        lat-deg1 (.lat p1)
        lon-deg2 (.lon p2)
        lat-deg2 (.lat p2)]
    (cond
      (is-north-pole? p2) due-north
      (is-north-pole? p1) due-south
      (is-south-pole? p2) due-south
      (is-south-pole? p1) due-north

      ;; vertical line
      (= lon-deg1 lon-deg2) (if (> lat-deg1 lat-deg2) due-south due-north)

      ;; This line will cross one of the poles. They are both on a vertical great circle.
      (= 180.0 (abs (- lon-deg1 lon-deg2)))
      ;;Check the average latitude to see if it's above the equator or below it
      (if (> (mid lat-deg1 lat-deg2) 0.0)
        due-north
        due-south)

      :else
      (let [lon1 (.lon_rad p1)
            lat1 (.lat_rad p1)
            lon2 (.lon_rad p2)
            lat2 (.lat_rad p2)
            lon2-lon1-diff (- lon2 lon1)
            cos-lat2 (cos lat2)
            y (* (sin lon2-lon1-diff) cos-lat2)
            x (- (* (cos lat1) (sin lat2))
                 (* (sin lat1) cos-lat2 (cos lon2-lon1-diff)))
            normalized (degrees (atan2 y x))]
        (mod (+ (* -1.0 normalized) 360.0) 360.0)))))

(extend-protocol ApproximateEquivalency
  Point
  (approx=
    ([expected n]
     (approx= expected n DELTA))
    ([^Point p1 ^Point p2 ^double delta]
     (let [lon1 (.lon p1)
           lat1 (.lat p1)
           lon2 (.lon p2)
           lat2 (.lat p2)
           np? #(double-approx= ^double % 90.0 delta)
           sp? #(double-approx= ^double % -90.0 delta)
           am? #(double-approx= ^double (abs ^double %) 180.0 delta)]
       (and (double-approx= lat1 lat2 delta)
            (or (double-approx= lon1 lon2 delta)
                (and (am? lon1) (am? lon2))
                (and (np? lat1) (np? lat2))
                (and (sp? lat1) (sp? lat2))))))))


(extend-protocol d/DerivedCalculator
  cmr.spatial.point.Point
  (calculate-derived
    ^Point [^Point point]
    point))

(defn- validate-point-longitude
  [{:keys [^double lon]}]
  (when (or (< lon -180.0)
            (> lon 180.0))
    [(msg/point-lon-invalid lon)]))

(defn- validate-point-latitude
  [{:keys [^double lat]}]
  (when (or (< lat -90.0)
            (> lat 90.0))
    [(msg/point-lat-invalid lat)]))

(extend-protocol v/SpatialValidation
  cmr.spatial.point.Point
  (validate
    [point]
    (concat (validate-point-longitude point)
            (validate-point-latitude point))))
