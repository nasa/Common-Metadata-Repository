(ns cmr.spatial.codec
  "Makes the spatial areas URL encodeable as accepted on the Catalog REST API"
  (:require
   [clojure.string :as string]
   [cmr.common.regex-builder :as rb]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.spatial.circle :as spatial-circle]
   [cmr.spatial.geodetic-ring :as gr]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.mbr :as mbr]
   [cmr.spatial.messages :as smsg]
   [cmr.spatial.point :as p]
   [cmr.spatial.polygon :as poly]
   [cmr.spatial.ring-relations :as rr]))

(defprotocol SpatialUrlEncode
  (url-encode [shape] "Encodes the spatial area for inclusion in a URL."))

(defn- encode-points
  "URL encodes a list of points"
  [points]
  (string/join "," (map url-encode points)))

(defn join-ordinates
  "Joins the ordinates together with commas"
  [& ords]
  (string/join "," (map util/double->string ords)))

(extend-protocol SpatialUrlEncode
  cmr.spatial.point.Point
  (url-encode
    [{:keys [lon lat]}]
    (join-ordinates lon lat))

  cmr.spatial.geodetic_ring.GeodeticRing
  (url-encode
    [{:keys [points]}]
    (encode-points points))

  cmr.spatial.line_string.LineString
  (url-encode
    [{:keys [points]}]
    (encode-points points))

  cmr.spatial.polygon.Polygon
  (url-encode
    [{:keys [rings]}]
    (when (> (count rings) 1)
      (errors/internal-error! "Polygons with holes can not be encoded."))
    (url-encode (first rings)))

  cmr.spatial.circle.Circle
  (url-encode
    [{:keys [center radius]}]
    (join-ordinates (:lon center) (:lat center) radius))

  cmr.spatial.mbr.Mbr
  (url-encode
    [{:keys [west north east south]}]
    (join-ordinates west south east north)))


(defmulti url-decode
  "Decodes a url encoded spatial shape back into the spatial shape. If there is an error decoding
  It returns a map with :errors key"
  (fn [type s]
    type))

(def point-regex
  (rb/compile-regex (rb/group (rb/capture rb/decimal-number)
                              ","
                              (rb/capture rb/decimal-number))))

(def polygon-regex
  (let [point (rb/group rb/decimal-number "," rb/decimal-number)]
    (rb/compile-regex (rb/group point (rb/n-or-more-times 3 "," point)))))

(def line-regex
  (let [point (rb/group rb/decimal-number "," rb/decimal-number)]
    (rb/compile-regex (rb/group point (rb/n-or-more-times 1 "," point)))))

(def mbr-regex
  (let [captured-num (rb/capture rb/decimal-number)]
    (rb/compile-regex (rb/group captured-num
                                "," captured-num
                                "," captured-num
                                "," captured-num))))

(def circle-regex
  (let [captured-num (rb/capture rb/decimal-number)]
    (rb/compile-regex (rb/group captured-num
                                "," captured-num
                                "," captured-num))))

(defmethod url-decode :point
  [type s]
  (if-let [match (re-matches point-regex s)]
    (let [[_ ^String lon-s ^String lat-s] match]
      (p/point (Double. lon-s) (Double. lat-s)))
    {:errors [(smsg/shape-decode-msg :point s)]}))

(defmethod url-decode :bounding-box
  [type s]
  (if-let [match (re-matches mbr-regex s)]
    (let [[_
           ^String w
           ^String s
           ^String e
           ^String n] match]
      (mbr/mbr (Double. w) (Double. n) (Double. e) (Double. s)))
    {:errors [(smsg/shape-decode-msg :bounding-box s)]}))

(defmethod url-decode :polygon
  [type s]
  (if-let [match (re-matches polygon-regex s)]
    (let [ordinates (map #(Double. ^String %) (string/split s #","))]
      (poly/polygon :geodetic [(rr/ords->ring :geodetic ordinates)]))
    {:errors [(smsg/shape-decode-msg :polygon s)]}))

(defmethod url-decode :line
  [type s]
  (if-let [match (re-matches line-regex s)]
    (let [ordinates (map #(Double. ^String %) (string/split s #","))]
      (l/ords->line-string :geodetic ordinates))
    {:errors [(smsg/shape-decode-msg :line s)]}))

(defmethod url-decode :circle
  [type s]
  (if-let [match (re-matches circle-regex s)]
    (let [[_ ^String lon ^String lat ^String radius] match]
      (if-let [error-msgs (spatial-circle/validate-radius (Double. radius))]
        {:errors error-msgs}
        (spatial-circle/circle (Double. lon) (Double. lat) (Double. radius))))
    {:errors [(smsg/shape-decode-msg :circle s)]}))
