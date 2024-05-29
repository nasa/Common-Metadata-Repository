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

(def polygon-regex #"(?:(?:(?:[+\-]?\d+(?:\.\d+)?),(?:[+\-]?\d+(?:\.\d+)?))(?:,(?:(?:[+\-]?\d+(?:\.\d+)?),(?:[+\-]?\d+(?:\.\d+)?))){3,})")

(def line-regex #"(?:(?:(?:[+\-]?\d+(?:\.\d+)?),(?:[+\-]?\d+(?:\.\d+)?))(?:,(?:(?:[+\-]?\d+(?:\.\d+)?),(?:[+\-]?\d+(?:\.\d+)?))){1,})")

(def mbr-regex #"(?:((?:[+\-]?\d+(?:\.\d+)?)),((?:[+\-]?\d+(?:\.\d+)?)),((?:[+\-]?\d+(?:\.\d+)?)),((?:[+\-]?\d+(?:\.\d+)?)))")

(def circle-regex #"(?:((?:[+\-]?\d+(?:\.\d+)?)),((?:[+\-]?\d+(?:\.\d+)?)),((?:[+\-]?\d+(?:\.\d+)?)))")

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
  (if (re-matches polygon-regex s)
    (let [ordinates (map #(Double. ^String %) (string/split s #","))]
      (poly/polygon :geodetic [(rr/ords->ring :geodetic ordinates)]))
    {:errors [(smsg/shape-decode-msg :polygon s)]}))

(defmethod url-decode :line
  [_type s]
  ;; If the line string is too large, we will experience a StackOverflowError in the java Regex code.
  ;; To avoid this, when the string is large enough we just split the string verify each token is a number
  ;; And that here is an even amount. Through testing we've determined about 660ish points is the limit,
  ;; which is the equivalent to about a 5000 characters long string, so to branch the logic away from
  ;; the Regex validation, we will count 4500 char strings or above as too large.
  (if (> (count s) 4500)
    (let [split-line-str (string/split s #",")]
      (if (or (some #(not (util/numeric-string? %)) split-line-str)
              (odd? (count split-line-str)))
        {:errors [(smsg/shape-decode-msg :line s)]}
        ;; The maximum shape file size by default is around 5000, we are mirroring that configuration
        ;; for the too-many-points validation.
        (if (> (/ (count split-line-str) 2) (smsg/max-line-points))
          {:errors [(smsg/line-too-many-points-msg :line s)]}
          (let [ordinates (map #(Double. ^String %) split-line-str)]
            (l/ords->line-string :geodetic ordinates)))))
    (if (re-matches line-regex s)
      (let [ordinates (map #(Double. ^String %) (string/split s #","))]
        (l/ords->line-string :geodetic ordinates))
      {:errors [(smsg/shape-decode-msg :line s)]})))

(defmethod url-decode :circle
  [_type s]
  (if-let [match (re-matches circle-regex s)]
    (let [[_ ^String lon ^String lat ^String radius] match]
      (if-let [error-msgs (spatial-circle/validate-radius (Double. radius))]
        {:errors error-msgs}
        (spatial-circle/circle (Double. lon) (Double. lat) (Double. radius))))
    {:errors [(smsg/shape-decode-msg :circle s)]}))
