(ns cmr.umm.echo10.spatial
  "Contains functions for convert spatial to and parsing from ECHO10 XML."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.line :as l]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.ring :as r])
  (:import java.text.DecimalFormat))

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
  (l/line (map parse-geometry (:content element))))

(defmethod parse-geometry :Boundary
  [element]
  (let [points (reverse (map parse-geometry (:content element)))
        points (concat points [(first points)])]
    (r/ring points)))

(defmethod parse-geometry :BoundingRectangle
  [element]
  (let [west (cx/double-at-path element [:WestBoundingCoordinate])
        east (cx/double-at-path element [:EastBoundingCoordinate])
        north (cx/double-at-path element [:NorthBoundingCoordinate])
        south (cx/double-at-path element [:SouthBoundingCoordinate])]
    (mbr/mbr west north east south)))

(defn geometry-element->geometries
  "Converts a Geometry element into a sequence of spatial geometry objects"
  [geom-elem]
  (map parse-geometry (:content geom-elem)))

(defprotocol ShapeToXml
  "Protocol for converting a shape into XML."

  (shape-to-xml
    [shape]
    "Converts the shape into a XML struct element"))

(defn generate-geometry-xml
  [geometries]
  (x/element :Geometry {}
             (map shape-to-xml geometries)))

(defn double->string
  "Converts a double to string without using exponential notation or loss of accuracy."
  [d]
  (.format (DecimalFormat. "#.#####################") d))

(extend-protocol ShapeToXml
  cmr.spatial.point.Point
  (shape-to-xml
    [{:keys [lon lat]}]
    (x/element :Point {}
               (x/element :PointLongitude {} (double->string lon))
               (x/element :PointLatitude {} (double->string lat))))

  cmr.spatial.mbr.Mbr
  (shape-to-xml
    [{:keys [west north east south]}]
    (x/element :BoundingRectangle {}
               (x/element :WestBoundingCoordinate {} (double->string west))
               (x/element :NorthBoundingCoordinate {} (double->string north))
               (x/element :EastBoundingCoordinate {} (double->string east))
               (x/element :SouthBoundingCoordinate {} (double->string south))))

  cmr.spatial.line.Line
  (shape-to-xml
    [{:keys [points]}]
    (x/element :Line {} (map shape-to-xml points)))

  cmr.spatial.ring.Ring
  (shape-to-xml
    [{:keys [points]}]
    (x/element :Boundary {}
               (map shape-to-xml
                    ;; Points must be specified in clockwise order and not closed.
                    (-> points
                        ; drop first point since last point will match
                        drop-last
                        ; counter clocwise to clockwise
                        reverse))))

  cmr.spatial.polygon.Polygon
  (shape-to-xml
    [{:keys [rings]}]
    (let [boundary (first rings)
          holes (seq (rest rings))]
      (x/element :GPolygon {}
                 (shape-to-xml boundary)
                 (when holes
                   (x/element :ExclusiveZone {} (map shape-to-xml holes)))))))

