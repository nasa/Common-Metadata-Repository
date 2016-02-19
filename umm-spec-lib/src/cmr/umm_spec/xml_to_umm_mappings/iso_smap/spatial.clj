(ns cmr.umm-spec.xml-to-umm-mappings.iso-smap.spatial
  "Functions for parsing UMM spatial records out of ISO SMAP XML documents."
  (:require [cmr.common.xml.simple-xpath :refer [select]]
            [cmr.common.xml.parse :refer :all]))

(def bounding-rectangles-xpath-str
  "gmd:extent/gmd:EX_Extent/gmd:geographicElement/gmd:EX_GeographicBoundingBox")

(defn- parse-bounding-rectangle
  "Returns the parsed bounding rectangle from the given bounding rectangle xml document."
  [br-el]
  (let [coordinate-fn (fn [coord-path]
                        (when-let [coord (value-of br-el (str coord-path "/gco:Decimal"))]
                          (Double. coord)))]
    {:NorthBoundingCoordinate (coordinate-fn "gmd:northBoundLatitude")
     :SouthBoundingCoordinate (coordinate-fn "gmd:southBoundLatitude")
     :WestBoundingCoordinate (coordinate-fn "gmd:westBoundLongitude")
     :EastBoundingCoordinate (coordinate-fn "gmd:eastBoundLongitude")}))

(defn- parse-bounding-rectangles
  "Returns the parsed bounding rectangles from the given xml document."
  [data-id-el]
  (seq (map parse-bounding-rectangle
            (select data-id-el bounding-rectangles-xpath-str))))

(defn parse-spatial
  "Returns UMM SpatialExtentType map from SMAP ISO data identifier XML document."
  [data-id-el]
  (when-let [bounding-rectangles (parse-bounding-rectangles data-id-el)]
    {:SpatialCoverageType "GEODETIC"
     :GranuleSpatialRepresentation "GEODETIC"
     :HorizontalSpatialDomain {:Geometry {:CoordinateSystem "GEODETIC"
                                          :BoundingRectangles bounding-rectangles}}}))