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
   ^clojure.lang.Associative options
   ]
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
          (pj/assert (double-approx= (radians lon) lon-rad))
          (pj/assert (double-approx= (radians lat) lat-rad))

          (Point. (double lon)
                  (double lat)
                  (double lon-rad)
                  (double lat-rad)
                  geodetic-equality
                  nil)))

(def north-pole
  (point 0 90))

(def south-pole
  (point 0 -90))

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

(defn order-longitudes
  "Orders the longitudes from west to east such that traveling east crosses at most 180 degrees."
  [^double l1 ^double l2]
  (let [^double mod (mod (- l1 l2) 360)]
    (cond
      ;; use natural ordering in this case
      (= mod 180.0) (sort [l1 l2])
      (= mod 0.0) (if (= l1 180.0) [l1 l2] [l2 l1])
      (< mod 180.0) [l2 l1]
      :else [l1 l2])))

(defn order-points
  "Orders the points from west to east using the same rules as order longitudes. If points are the
  same longitude ordered north to south"
  [^Point p1 ^Point p2]
  (let [lon1 (.lon p1)
        lat1 (.lat p1)
        lon2 (.lon p2)
        lat2 (.lat p2)]
    (if (= lon1 lon2)
      ;; Order by latitudes
      (if (> lat2 lat1)
        [p1 p2]
        [p2 p1])
      ;; order by longitudes
      (let [[west-lon east-lon] (order-longitudes lon1 lon2)]
        (if (= lon1 west-lon)
          [p1 p2]
          [p2 p1])))))

(defn round-point
  "Rounds the point the given number of decimal places"
  [num-places p]
  (let [{:keys [lon lat geodetic-equality]} p
        lon (round num-places lon)
        lat (round num-places lat)]
    (point lon lat geodetic-equality)))

(defn ords->points
  "Takes pairs of numbers and returns a sequence of points.

  user=> (ords->points 1 2 3 4)
  ((cmr-spatial.point/point 1.0 2.0) (cmr-spatial.point/point 3.0 4.0))"
  [& ords]
  (util/map-n (partial apply point) 2 ords))

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
  (approx= (.lat p) 90.0 0.0000001))

(defn is-south-pole? [^Point p]
  (approx= (.lat p) -90.0 0.0000001))

(defn is-pole? [p]
  (or (is-north-pole? p)
      (is-south-pole? p)))

(defn on-antimeridian? [p]
  (or (= (abs (:lon p)) 180.0)
      (is-north-pole? p)
      (is-south-pole? p)))

(defn angular-distance
  "Returns the angular distance between the points in radians
  From: http://williams.best.vwh.net/avform.htm#Dist"
  ^double [^Point p1 ^Point p2]
  (pj/assert (not (antipodal? p1 p2)))
  (let [lon1 (.lon_rad p1)
        lat1 (.lat_rad p1)
        lon2 (.lon_rad p2)
        lat2 (.lat_rad p2)
        sin-sq (fn [^double v1 ^double v2]
                 (sq (sin (/ (- v1 v2) 2.0))))
        ^double part1 (sin-sq lat1 lat2)
        part2 (* (cos lat1) (cos lat2) (double (sin-sq lon1 lon2)))]
    (* 2.0 (asin (sqrt (+ part1 part2))))))


(defn course
  "Returns the initial bearing between two points. The bearing starts at 0 pointing towards the north
  pole and increases clockwise. 180 points to the south pole. 360 points the same direction as 0.
  Algorithm from: http://williams.best.vwh.net/avform.htm#Crs"
  ^double [^Point p1 ^Point p2]
  (pj/assert (not= p1 p2))
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
      (let [d (angular-distance p1 p2)
            lon1 (.lon_rad p1)
            lat1 (.lat_rad p1)
            lon2 (.lon_rad p2)
            lat2 (.lat_rad p2)
            part1 (- (sin lat2) (* (sin lat1) (cos d)))
            part2 (* (sin d) (cos lat1))
            part3 (/ part1 part2)

            ;; Avoid numerical errors with vertical lines
            part3 (if (> (abs part3) 1.0)
                    (if (> (- (abs part3) 1.0) 0.0001)
                      (throw (Exception.
                               (str "Completely unexpected result for part3 while generating course."
                                    "Expected some value ~ between -1 and 1. Result:" part3
                                    "Points [" p1 p2 "]")))
                      (if (> part3 0.0) 1 -1))
                    part3)
            angle (degrees (acos part3))]
        (cond
          (>= (sin (- lon2 lon1)) 0.0) (- 360.0 angle)
          (= angle 360.0) 0.0
          :else angle)))))

(extend-protocol ApproximateEquivalency
  Point
  (approx=
    ([expected n]
     (approx= expected n DELTA))
    ([p1 p2 delta]
     (let [{lon1 :lon lat1 :lat} p1
           {lon2 :lon lat2 :lat} p2
           np? #(approx= % 90.0 delta)
           sp? #(approx= % -90.0 delta)
           am? #(approx= (abs %) 180.0 delta)]
       (and (approx= lat1 lat2 delta)
            (or (approx= lon1 lon2 delta)
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






