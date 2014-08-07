(ns cmr.umm.echo10.spatial
  "Contains functions for convert spatial to and parsing from ECHO10 XML."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.line-string :as l]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.geodetic-ring :as gr]
            [cmr.spatial.cartesian-ring :as cr]
            [cmr.umm.spatial :as umm-s]
            [cmr.common.util :as util]))

(defmulti parse-geometry
  "Parses a geometry element based on the tag of the element."
  (fn [element]
    (:tag element)))

(defmethod parse-geometry :GPolygon
  [element]
  (let [outer-ring (parse-geometry (cx/element-at-path element [:Boundary]))
        holes (map parse-geometry (cx/elements-at-path element [:ExclusiveZone :Boundary]))]
    (poly/polygon (cons outer-ring holes))))

(defmethod parse-geometry :Point
  [element]
  (let [lon (cx/double-at-path element [:PointLongitude])
        lat (cx/double-at-path element [:PointLatitude])]
  (p/point lon lat)))

(defmethod parse-geometry :Line
  [element]
  (l/line-string (map parse-geometry (:content element))))

(defmethod parse-geometry :Boundary
  [element]
  (let [points (reverse (map parse-geometry (:content element)))
        points (concat points [(first points)])]
    (umm-s/ring points)))

(defmethod parse-geometry :BoundingRectangle
  [element]
  (let [west (cx/double-at-path element [:WestBoundingCoordinate])
        east (cx/double-at-path element [:EastBoundingCoordinate])
        north (cx/double-at-path element [:NorthBoundingCoordinate])
        south (cx/double-at-path element [:SouthBoundingCoordinate])]
    (mbr/mbr west north east south)))

(def geometry-tags
  "The list of geometry tags in the geometry element that are actual spatial area elements"
  #{:GPolygon :Point :Line :Boundary :BoundingRectangle})

(defn geometry-element->geometries
  "Converts a Geometry element into a sequence of spatial geometry objects"
  [geom-elem]
  (map parse-geometry (filter (comp geometry-tags :tag) (:content geom-elem))))

(defprotocol ShapeToXml
  "Protocol for converting a shape into XML."

  (shape-to-xml
    [shape]
    "Converts the shape into a XML struct element"))

(defn generate-geometry-xml
  [geometries]
  (x/element :Geometry {}
             (map shape-to-xml geometries)))

(defn- ring-to-xml
  [ring]
  (x/element :Boundary {}
             (map shape-to-xml
                  ;; Points must be specified in clockwise order and not closed.
                  (-> (:points ring)
                      ; drop first point since last point will match
                      drop-last
                      ; counter clocwise to clockwise
                      reverse))))

(extend-protocol ShapeToXml
  cmr.spatial.point.Point
  (shape-to-xml
    [{:keys [lon lat]}]
    (x/element :Point {}
               (x/element :PointLongitude {} (util/double->string lon))
               (x/element :PointLatitude {} (util/double->string lat))))

  cmr.spatial.mbr.Mbr
  (shape-to-xml
    [{:keys [west north east south]}]
    (x/element :BoundingRectangle {}
               (x/element :WestBoundingCoordinate {} (util/double->string west))
               (x/element :NorthBoundingCoordinate {} (util/double->string north))
               (x/element :EastBoundingCoordinate {} (util/double->string east))
               (x/element :SouthBoundingCoordinate {} (util/double->string south))))

  cmr.spatial.line_string.LineString
  (shape-to-xml
    [{:keys [points]}]
    (x/element :Line {} (map shape-to-xml points)))

  cmr.spatial.geodetic_ring.GeodeticRing
  (shape-to-xml
    [ring]
    (ring-to-xml ring))

  cmr.spatial.cartesian_ring.CartesianRing
  (shape-to-xml
    [ring]
    (ring-to-xml ring))

  cmr.umm.spatial.GenericRing
  (shape-to-xml
    [ring]
    (ring-to-xml ring))

  cmr.spatial.polygon.Polygon
  (shape-to-xml
    [{:keys [rings]}]
    (let [boundary (first rings)
          holes (seq (rest rings))]
      (x/element :GPolygon {}
                 (shape-to-xml boundary)
                 (when holes
                   (x/element :ExclusiveZone {} (map shape-to-xml holes)))))))

