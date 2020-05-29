(ns cmr.spatial.circle
  (:require
   [cmr.spatial.math :as math :refer :all]
   [primitive-math]
   [cmr.spatial.point :as p]
   [cmr.spatial.derived :as d]
   [cmr.spatial.geodetic-ring :as gr]
   [cmr.spatial.validation :as sv]
   [cmr.spatial.polygon :as poly]
   [cmr.common.dev.record-pretty-printer :as record-pretty-printer])
  (:import
   cmr.spatial.geodetic_ring.GeodeticRing
   cmr.spatial.polygon.Polygon
   cmr.spatial.point.Point))

(primitive-math/use-primitive-operators)

;; Circle
(defrecord Circle
  [
   ^Point center
   ^double radius
   ])

(record-pretty-printer/enable-record-pretty-printing Circle)

(defn circle
  "Creates a circle"
  ([^Point center ^double radius]
   (->Circle center radius))
  ([^double lon ^double lat ^double radius]
   (->Circle (p/point lon lat) radius)))

(def MIN_RADIUS
  "Minimum radius in meters"
  10)

(def MAX_RADIUS
  "Maximum radius in meters"
  6000000)

(def ^:const ^double EARTH_RADIUS_APPROX
  "Radius of the earth in meters for polygon approximation of the circle"
  6378137.0)

(defn- validate-center
  "Validate the center of the circle, returns error if it is on the poles; otherwise returns nil"
  [^Point center]
  (let [{:keys [lon lat]} center]
    (when (or (= 90.0 lat) (= -90.0 lat))
      [(format "Circle center cannot be the north or south pole, but was [%s, %s]" lon lat)])))

(defn validate-radius
  "Validate the radius. Returns a list of errors when radius is invalid; otherwise returns nil"
  [^double radius]
  (when-not (<= (double MIN_RADIUS) radius (double MAX_RADIUS))
    [(format "Circle radius must be between %s and %s, but was %s." MIN_RADIUS MAX_RADIUS radius)]))

(defn covers-point?
  "Returns true if the circle contains the given point"
  [^Circle cir ^Point point]
  (let [{:keys [^Point center ^double radius]} cir]
    (>= radius (p/distance center point))))

(defn- sanitized-longitude
  "Returns the sanitized longitude value"
  [^double lon]
  (if (> lon 180.0)
    (- lon 360.0)
    (if (< lon -180.0)
      (+ lon 360.0)
      lon)))

(defn circle->polygon
  "Returns the polygon approximation of the circle with the given number of points.
   Reference: https://github.com/gabzim/circle-to-polygon."
  [^Circle cir ^long n]
  (let [{:keys [^Point center ^double radius]} cir
        lon1 (.lon_rad center)
        lat1 (.lat_rad center)
        r-rad (/ radius EARTH_RADIUS_APPROX)
        points (persistent!
                (reduce (fn [pts ^long index]
                          (let [theta (* -2.0 PI (/ (double index) (double n)))
                                plat-rad (asin
                                          (+ (* (sin lat1) (cos r-rad))
                                             (* (cos lat1) (sin r-rad) (cos theta))))
                                plon-delta (atan2
                                            (* (sin theta) (sin r-rad) (cos lat1))
                                            (- (cos r-rad) (* (sin lat1) (sin plat-rad))))
                                plon (-> (+ lon1 plon-delta)
                                         degrees
                                         sanitized-longitude)
                                plat (degrees plat-rad)]
                            (conj! pts (p/point plon plat))))
                        (transient [])
                        (range 0 (inc n))))
        ;; sometimes there are precision issues, causing last point not exactly matching first,
        ;; replace the last one with the first one to guarantee the polygon is complete and valid
        poly-points (conj (pop points) (first points))]

    (poly/polygon :geodetic [(gr/ring poly-points)])))

(extend-protocol d/DerivedCalculator
  cmr.spatial.circle.Circle
  (calculate-derived
    ^Circle [^Circle cir]
    cir))

(extend-protocol sv/SpatialValidation
  cmr.spatial.circle.Circle
  (validate
   [record]
   (let [{:keys [center radius]} record]
     (concat (sv/validate center)
             (validate-center center)
             (validate-radius radius)))))
