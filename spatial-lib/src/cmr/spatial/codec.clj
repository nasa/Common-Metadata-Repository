(ns cmr.spatial.codec
  "Makes the spatial areas URL encodeable as accepted on the Catalog REST API"
  (:require
   [clojure.string :as string]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.spatial.circle :as spatial-circle]
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
  (fn [type _s]
    type))

(def point-regex #"(?:((?:[+\-]?\d+(?:\.\d+)?)),((?:[+\-]?\d+(?:\.\d+)?)))")

(def ordinate-regex #"[+\-]?\d+(?:\.\d+)?")

(def mbr-regex #"(?:((?:[+\-]?\d+(?:\.\d+)?)),((?:[+\-]?\d+(?:\.\d+)?)),((?:[+\-]?\d+(?:\.\d+)?)),((?:[+\-]?\d+(?:\.\d+)?)))")

(def circle-regex #"(?:((?:[+\-]?\d+(?:\.\d+)?)),((?:[+\-]?\d+(?:\.\d+)?)),((?:[+\-]?\d+(?:\.\d+)?)))")

(defn- parse-ordinates
  "Returns the ordinates in s as doubles, or nil if s is not a comma separated list of at least
  min-points lon,lat pairs. Each ordinate is matched on its own because a single regex over the
  whole list overflows the stack on large shapes."
  [s min-points]
  (let [ords (string/split s #"," -1)
        matcher (re-matcher ordinate-regex "")]
    (when (and (even? (count ords))
               (>= (count ords) (* 2 min-points))
               (every? #(.matches (.reset matcher ^String %)) ords))
      (mapv #(Double. ^String %) ords))))

(defmethod url-decode :point
  [_type s]
  (if-let [match (re-matches point-regex s)]
    (let [[_ ^String lon-s ^String lat-s] match]
      (p/point (Double. lon-s) (Double. lat-s)))
    {:errors [(smsg/shape-decode-msg :point s)]}))

(defmethod url-decode :bounding-box
  [_type s]
  (if-let [match (re-matches mbr-regex s)]
    (let [[_
           ^String w
           ^String s
           ^String e
           ^String n] match]
      (mbr/mbr (Double. w) (Double. n) (Double. e) (Double. s)))
    {:errors [(smsg/shape-decode-msg :bounding-box s)]}))

(defmethod url-decode :polygon
  [_type s]
  (if-let [ordinates (parse-ordinates s 4)]
    (if (> (/ (count ordinates) 2) (smsg/max-polygon-points))
      {:errors [(smsg/line-too-many-points-msg :polygon s)]}
      (poly/polygon :geodetic [(rr/ords->ring :geodetic ordinates)]))
    {:errors [(smsg/shape-decode-msg :polygon s)]}))

(defmethod url-decode :line
  [_type s]
  (if-let [ordinates (parse-ordinates s 2)]
    (if (> (/ (count ordinates) 2) (smsg/max-line-points))
      {:errors [(smsg/line-too-many-points-msg :line s)]}
      (l/ords->line-string :geodetic ordinates))
    {:errors [(smsg/shape-decode-msg :line s)]}))

(defmethod url-decode :circle
  [_type s]
  (if-let [match (re-matches circle-regex s)]
    (let [[_ ^String lon ^String lat ^String radius] match]
      (if-let [error-msgs (spatial-circle/validate-radius (Double. radius))]
        {:errors error-msgs}
        (spatial-circle/circle (Double. lon) (Double. lat) (Double. radius))))
    {:errors [(smsg/shape-decode-msg :circle s)]}))
