(ns cmr.spatial.encoding.gml
  "Functions for dealing with spatial data in the GML XML format."
  (:require [clojure.data.xml :as x]
            [clojure.string :as string]
            [cmr.common.util :as util]
            [cmr.common.xml :as cx]
            [cmr.spatial.encoding.core :refer :all]
            [cmr.spatial.line-string :as line]
            [cmr.spatial.point :as p])
  (import cmr.spatial.point.Point
          cmr.spatial.line_string.LineString))

(defn parse-lat-lon-string
  "Converts a string of lat lon pairs separated by spaces into a list of points"
  [s]
  {:pre [(string? s)]}
  (->> (re-seq #"(\-|)\d+(\.\d+|)" s)
       (map first)
       (map #(Double/parseDouble %))
       (partition 2)
       (map (fn [[lat lon]]
              (p/point lon lat)))))

(defn lat-lon-string
  "Returns a GML lat-lon coordinate string from a sequence of points."
  [points]
  (string/join " " (map util/double->string (mapcat (juxt :lat :lon) points))))

;; Points

(defmethod encode [:gml Point]
  [_ point]
  (x/element :gml:Point {}
             (x/element :gml:pos {} (lat-lon-string [point]))))

(defmethod decode [:gml :Point]
  [_ element]
  (first (parse-lat-lon-string (cx/string-at-path element [:pos]))))

;; LineStrings

(defmethod encode [:gml LineString]
  [_ line]
  (x/element :gml:LineString {}
             (x/element :gml:posList {} (lat-lon-string (:points line)))))

(defmethod decode [:gml :LineString]
  [_ element]
  (line/line-string (parse-lat-lon-string (cx/string-at-path element [:posList]))))
