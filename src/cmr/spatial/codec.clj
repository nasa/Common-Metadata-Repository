(ns cmr.spatial.codec
  "Makes the spatial areas URL encodeable as accepted on the Catalog REST API"
  (:require [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.ring :as r]
            [cmr.spatial.mbr :as mbr]
            [cmr.common.regex-builder :as rb]
            [clojure.string :as str]
            [cmr.common.services.errors :as errors]
            [cmr.spatial.codec-messages :as cmsg]
            [cmr.common.util :as util]))


(defprotocol SpatialUrlEncode
  (url-encode [shape] "Encodes the spatial area for inclusion in a URL."))


(defn- encode-points
  "URL encodes a list of points"
  [points]
  (str/join "," (map url-encode points)))

(defn join-ordinates
  "Joins the ordinates together with commas"
  [& ords]
  (str/join "," (map util/double->string ords)))


(extend-protocol SpatialUrlEncode
  cmr.spatial.point.Point
  (url-encode
    [{:keys [lon lat]}]
    (join-ordinates lon lat))

  cmr.spatial.ring.Ring
  (url-encode
    [{:keys [points]}]
    (encode-points points))

  cmr.spatial.polygon.Polygon
  (url-encode
    [{:keys [rings]}]
    (when (> (count rings) 1)
      (errors/internal-error! "Polygons with holes can not be encoded."))
    (url-encode (first rings)))

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

(def mbr-regex
  (let [captured-num (rb/capture rb/decimal-number)]
    (rb/compile-regex (rb/group captured-num
                                "," captured-num
                                "," captured-num
                                "," captured-num))))

(defmethod url-decode :point
  [type s]
  (if-let [match (re-matches point-regex s)]
    (let [[_ ^String lon-s ^String lat-s] match]
      (p/point (Double. lon-s) (Double. lat-s)))
    {:errors [(cmsg/shape-decode-msg :point s)]}))

(defmethod url-decode :br
  [type s]
  (if-let [match (re-matches mbr-regex s)]
    (let [[_
           ^String w
           ^String s
           ^String e
           ^String n] match]
      (mbr/mbr (Double. w) (Double. n) (Double. e) (Double. s)))
    {:errors [(cmsg/shape-decode-msg :bounding-box s)]}))

(defmethod url-decode :polygon
  [type s]
  (if-let [match (re-matches polygon-regex s)]
    (let [ordinates (map #(Double. ^String %) (str/split s #","))]
      (poly/polygon [(apply r/ords->ring ordinates)]))
    {:errors [(cmsg/shape-decode-msg :polygon s)]}))






