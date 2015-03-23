(ns cmr.spatial.encoding.gml
  "Functions for dealing with spatial data in the GML XML format.
  Spatial objects parsed from GML default to a :cartesian coordinate
  system."
  (:require [clojure.data.xml :as x]
            [clojure.string :as string]
            [cmr.common.util :as util]
            [cmr.common.xml :as cx]
            [cmr.spatial.line-string :as line]
            [cmr.spatial.point :as p]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.ring-relations :as rr]))

;; Core Interface

(defmulti encode
  "Returns a GML XML element from a CMR spatial object."
  type)

(defmulti decode
  "Returns a CMR spatial object from a GML XML element (as parsed by clojure.data.xml)."
  :tag)

;; Helpers

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

(defn- gml-linear-ring
  "Returns a gml:LinearRing element from a CMR ring structure."
  [{:keys [points]}]
  (x/element :gml:LinearRing {}
             (x/element :gml:posList {} (lat-lon-string points))))

;; Points

(defmethod encode cmr.spatial.point.Point
  [point]
  (x/element :gml:Point {}
             (x/element :gml:pos {} (lat-lon-string [point]))))

(defmethod decode :Point
  [element]
  (first (parse-lat-lon-string (cx/string-at-path element [:pos]))))

;; LineStrings

(defmethod encode cmr.spatial.line_string.LineString
  [line]
  (x/element :gml:LineString {}
             (x/element :gml:posList {} (lat-lon-string (:points line)))))

(defmethod decode :LineString
  [element]
  (line/line-string :cartesian (parse-lat-lon-string (cx/string-at-path element [:posList]))))

;; Polygons

(defmethod encode cmr.spatial.polygon.Polygon
  [polygon]
  (x/element :gml:Polygon {}
             (x/element :gml:exterior {} (gml-linear-ring (poly/boundary polygon)))
             (map #(x/element :gml:interior {} (gml-linear-ring %)) (poly/holes polygon))))

(defmethod decode :Polygon
  [element]
  (let [exterior  (parse-lat-lon-string (cx/string-at-path element [:exterior :LinearRing :posList]))
        interiors (map parse-lat-lon-string (cx/strings-at-path element [:interior :LinearRing :posList]))
        ring      #(rr/ring :cartesian %)
        rings     (cons (ring exterior)
                        (map ring interiors))]
    (poly/polygon rings)))
