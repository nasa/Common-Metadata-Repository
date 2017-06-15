(ns cmr.spatial.encoding.gml
  "Functions for dealing with spatial data in the GML XML format.
  Spatial objects parsed from GML default to a :cartesian coordinate
  system.

  The OpenGIS Geography Markup Language Encoding Standard (GML) The
  Geography Markup Language (GML) is an XML grammar for expressing
  geographical features. GML serves as a modeling language for
  geographic systems as well as an open interchange format for
  geographic transactions on the Internet.

  http://www.opengeospatial.org/standards/gml"
  (:require [clojure.data.xml :as x]
            [clojure.string :as string]
            [cmr.common.util :as util]
            [cmr.common.xml :as cx]
            [cmr.spatial.line-string :as line]
            [cmr.spatial.point :as p]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.ring-relations :as rr]))

(def xml-namespace
  "XML namespace defining GML elements."
  "http://www.opengis.net/gml/3.2")

;; Core Interface

(defmulti encode
  "Returns a GML XML element from a CMR spatial object."
  type)

(defmulti decode
  "Returns a CMR spatial object from a GML XML element (as parsed by clojure.data.xml)."
  :tag)

;; Helpers

(defn parse-srs
  "Returns a CMR coordinate system keyword from a SRS string (URL or
  short string)."
  [s]
  (condp re-find s
    #"EPSG:9825|EPSG/0/9825" :cartesian
    #"EPSG:4326|EPSG/0/4326" :geodetic
    nil))

(def srs-url
  "Returns the canonical URL for a coordinate system keyword."
  {:cartesian "http://www.opengis.net/def/crs/EPSG/0/9825"
   :geodetic  "http://www.opengis.net/def/crs/EPSG/0/4326"})

(defn parse-lat-lon-string
  "Converts a string of lat lon pairs separated by spaces into a list of points"
  [s]
  {:pre [(string? s)]}
  (->> (re-seq #"\S+" s) ; split on whitespace
       (map #(Double/parseDouble %))
       (partition 2)
       (map (fn [[lat lon]]
              (p/point lon lat)))))

(defn lat-lon-string
  "Returns a GML lat-lon coordinate string from a sequence of points."
  [points]
  (string/join " " (map util/double->string (mapcat (juxt :lat :lon) points))))

(defn- gml-linear-ring
  "Returns a gml:LinearRing element from a CMR ring structure. Assumes anti-clockwise point ordering."
  [{:keys [points]}]
  (x/element :gml:LinearRing {}
             (x/element :gml:posList {} (lat-lon-string points))))

(defn- make-id
  []
  (str "geo-" (java.util.UUID/randomUUID)))

;; Points

(defmethod encode cmr.spatial.point.Point
  [point]
  (x/element :gml:Point {:gml:id (make-id)}
             (x/element :gml:pos {} (lat-lon-string [point]))))

(defmethod decode :Point
  [element]
  (first (parse-lat-lon-string (cx/string-at-path element [:pos]))))

;; LineStrings

(defmethod encode cmr.spatial.line_string.LineString
  [line]
  (x/element :gml:LineString {:gml:id (make-id)}
             (x/element :gml:posList {} (lat-lon-string (:points line)))))

(defmethod decode :LineString
  [element]
  (line/line-string :cartesian (parse-lat-lon-string (cx/string-at-path element [:posList]))))

;; Polygons

(defmethod encode cmr.spatial.polygon.Polygon
  [polygon]
  (x/element :gml:Polygon {:gml:id (make-id)
                           :srsName (srs-url (:coordinate-system polygon))}
             (x/element :gml:exterior {} (gml-linear-ring (poly/boundary polygon)))
             (map #(x/element :gml:interior {} (gml-linear-ring %)) (poly/holes polygon))))

(defmethod decode :Polygon
  [element]
  (let [exterior  (parse-lat-lon-string (cx/string-at-path element [:exterior :LinearRing :posList]))
        interiors (map parse-lat-lon-string (cx/strings-at-path element [:interior :LinearRing :posList]))
        srs       (or (some-> element :attrs :srsName parse-srs)
                      :cartesian)
        ring      #(rr/ring srs %)
        rings     (cons (ring exterior)
                        (map ring interiors))]
    (poly/polygon srs rings)))
