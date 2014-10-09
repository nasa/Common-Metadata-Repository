(ns cmr.umm.spatial
  "Contains some code to assist in representing spatial areas in UMM"
  (:require [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.point :as p]
            [cmr.spatial.polygon]
            [cmr.spatial.line-string]
            [cmr.spatial.geodetic-ring :as gr]
            [cmr.spatial.cartesian-ring :as cr]
            [cmr.common.services.errors :as errors]
            [cmr.common.dev.record-pretty-printer :as record-pretty-printer])
  (:import cmr.spatial.polygon.Polygon
           cmr.spatial.geodetic_ring.GeodeticRing
           cmr.spatial.cartesian_ring.CartesianRing
           cmr.spatial.line_string.LineString))

;; Represents a ring in which the coordinate system is not known at the time the data is constructed/
;; parsed. The coordinate system can be set at some later time and which will change the ring to a
;; specific type of ring.
(defrecord GenericRing
  [
   coordinate-system
   points
   ])
(record-pretty-printer/enable-record-pretty-printing GenericRing)

(defn ring
  "Constructs a generic ring with the specified points"
  [points]
  (->GenericRing nil points))

(defn ords->ring
  "Takes all arguments as coordinates for points, lon1, lat1, lon2, lat2, and creates a ring."
  [& ords]
  (ring (apply p/ords->points ords)))

(defmulti set-coordinate-system
  "Sets the coordinate system on the shape"
  (fn [coordinate-system shape]
    (type shape)))

(defmethod set-coordinate-system :default
  [coordinate-system shape]
  ;; Does nothing by default
  shape)

(defmethod set-coordinate-system Polygon
  [coordinate-system polygon]
  (-> polygon
      (assoc :coordinate-system coordinate-system)
      (update-in [:rings] (fn [rings]
                            (mapv (partial set-coordinate-system coordinate-system) rings)))))

(defmethod set-coordinate-system LineString
  [coordinate-system line]
  (assoc line :coordinate-system coordinate-system))

(defmethod set-coordinate-system GeodeticRing
  [coordinate-system {:keys [points]}]
  (rr/ring coordinate-system points))

(defmethod set-coordinate-system CartesianRing
  [coordinate-system {:keys [points]}]
  (rr/ring coordinate-system points))

(defmethod set-coordinate-system GenericRing
  [coordinate-system {:keys [points]}]
  (rr/ring coordinate-system points))
