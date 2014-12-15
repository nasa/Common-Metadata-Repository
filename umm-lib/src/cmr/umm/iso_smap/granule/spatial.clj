(ns cmr.umm.iso-smap.granule.spatial
  "Contains functions for parsing and generating the ISO SMAP granule spatial"
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.granule :as g]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.polygon :as poly]
            [cmr.umm.spatial :as spatial]
            [cmr.umm.generator-util :as gu]
            [cmr.umm.iso-smap.helper :as h]
            [cmr.umm.iso-smap.collection.spatial :as cs]))

(defmulti parse-geometry
  "Parses a geometry element based on the tag of the element."
  (fn [element]
    (:tag (first (:content element)))))

(defmethod parse-geometry :EX_GeographicBoundingBox
  [element]
  (let [element (cx/element-at-path element [:EX_GeographicBoundingBox])]
    (cs/bounding-box-elem->geometry element)))

(defmethod parse-geometry :EX_BoundingPolygon
  [element]
  (let [coords (cx/string-at-path
                 element [:EX_BoundingPolygon :polygon :Polygon :exterior :LinearRing :posList])]
    (poly/polygon [(spatial/ring-str->ring coords)])))

(def geometry-tags
  "The list of geometry tags in the geometry element that are actual spatial area elements"
  #{:EX_GeographicBoundingBox :EX_BoundingPolygon})

(defn xml-elem->SpatialCoverage
  "Returns a UMM SpatialCoverage from a parsed XML structure"
  [xml-struct]
  (let [spatial-elems (cx/elements-at-path
                        xml-struct
                        [:composedOf :DS_DataSet :has :MI_Metadata :identificationInfo :MD_DataIdentification
                         :extent :EX_Extent :geographicElement])
        spatial-elems (filter (comp geometry-tags :tag first :content) spatial-elems)]
    (when (seq spatial-elems)
      (let [geometries (map parse-geometry spatial-elems)]
        (g/map->SpatialCoverage {:geometries geometries})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generators

(defmulti generate-geometry-element
  "Generate spatial geometry element based the type of geometry."
  (fn [geometry]
    (type geometry)))

(defmethod generate-geometry-element cmr.spatial.mbr.Mbr
  [geometry]
  (cs/generate-bounding-box-element geometry))

(defn- generate-pos-list-element
  "Returns the polygon posList element for the given points string"
  [points-str]
  (x/element :gml:posList {:srsName "http://www.opengis.net/def/crs/EPSG/4326"
                           :srsDimension "2"}
             points-str))

(defmethod generate-geometry-element cmr.spatial.polygon.Polygon
  [geometry]
  (let [ring-str (spatial/ring->ring-str (first (:rings geometry)))]
    (x/element
      :gmd:geographicElement {}
      (x/element
        :gmd:EX_BoundingPolygon {}
        (x/element :gmd:extentTypeCode {}
                   (x/element :gco:Boolean {} 1))
        (x/element :gmd:polygon {}
                   (x/element :gml:Polygon {:gml:id (gu/generate-id)}
                              (x/element :gml:exterior {}
                                         (x/element :gml:LinearRing {}
                                                    (generate-pos-list-element ring-str)))))))))

(defn generate-spatial
  "Generates the Spatial element from spatial coverage"
  [spatial-coverage]
  (for [geometry (:geometries spatial-coverage)
        :let [gtype (type geometry)]
        :when (or (= cmr.spatial.mbr.Mbr gtype)
                  (= cmr.spatial.polygon.Polygon gtype))]
    (generate-geometry-element geometry)))


