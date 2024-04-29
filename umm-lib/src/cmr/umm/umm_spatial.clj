(ns cmr.umm.umm-spatial
  "Contains some code to assist in representing spatial areas in UMM"
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.string :as string]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.point :as p]
   [cmr.spatial.polygon]
   [cmr.spatial.ring-relations :as rr]
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
   points])

(record-pretty-printer/enable-record-pretty-printing GenericRing)

(defn ring
  "Constructs a generic ring with the specified points"
  [points]
  (->GenericRing nil points))

(defn ords->ring
  "Takes all arguments as coordinates for points, lon1, lat1, lon2, lat2, and creates a ring."
  [& ords]
  (ring (p/ords->points ords)))

(def valid-coord-systems #{:geodetic :cartesian})

(defn ->coordinate-system
  "Returns a normalized coordinate system keyword from a string."
  [s]
  (when s
    (get valid-coord-systems (csk/->kebab-case-keyword s))))

(defmulti set-coordinate-system
  "Sets the coordinate system on the shape"
  (fn [_coordinate-system shape]
    (type shape)))

(defmethod set-coordinate-system :default
  [_coordinate-system shape]
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
  (l/set-coordinate-system line coordinate-system))

(defmethod set-coordinate-system GeodeticRing
  [coordinate-system {:keys [points]}]
  (rr/ring coordinate-system points))

(defmethod set-coordinate-system CartesianRing
  [coordinate-system {:keys [points]}]
  (rr/ring coordinate-system points))

(defmethod set-coordinate-system GenericRing
  [coordinate-system {:keys [points]}]
  (rr/ring coordinate-system points))

(defn lat-lon-point-str->points
  "Converts a string of lat lon pairs separated by spaces into a list of points"
  [s]
  (->> (string/split s #" ")
       (map #(Double. ^String %))
       (partition 2)
       (map (fn [[lat lon]]
              (p/point lon lat)))))

(defmulti lat-lon-point-str->ring
  "Parses a string of lat lon pairs separated by spaces to a ring."
  (fn
    ([_s]
     :counter-clockwise)
    ([_s point-order]
     point-order)))

(defmethod lat-lon-point-str->ring :counter-clockwise
  [s & _args]
  (ring (lat-lon-point-str->points s)))

(defmethod lat-lon-point-str->ring :clockwise
  [s & _args]
  (ring (reverse (lat-lon-point-str->points s))))

(defmulti ring->lat-lon-point-str
  "Returns the ring represented by a string of lat lon pairs separated by spaces."
  (fn
    ([_s]
     :counter-clockwise)
    ([_s point-order]
     point-order)))

(defmethod ring->lat-lon-point-str :counter-clockwise
  [r & _args]
  (string/join " " (mapcat (fn [p] [(:lat p) (:lon p)]) (:points r))))

(defmethod ring->lat-lon-point-str :clockwise
  [r & _args]
  (string/join " " (mapcat (fn [p] [(:lat p) (:lon p)]) (reverse (:points r)))))
