(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2.spatial
  "Functions for parsing UMM spatial records out of ISO 19115-2 XML documents."
  (:require [clojure.string :as str]
            [cmr.umm-spec.iso19115-2-util :as iso]
            [cmr.common.xml.simple-xpath :refer [select text]]
            [cmr.common.xml.parse :refer :all]
            [cmr.spatial.encoding.gmd :as gmd]))

(def coordinate-system-xpath
  (str "/gmi:MI_Metadata/gmd:referenceSystemInfo/gmd:MD_ReferenceSystem"
       "/gmd:referenceSystemIdentifier/gmd:RS_Identifier/gmd:code/gco:CharacterString"))

(def geographic-element-xpath
  (str iso/extent-xpath "/gmd:geographicElement"))

(def orbit-string-xpath
  (let [id-xpath "gmd:EX_GeographicDescription/gmd:geographicIdentifier/gmd:MD_Identifier"]
    (str geographic-element-xpath
         "[" id-xpath "/gmd:code/gco:CharacterString='Orbit']"
         "/" id-xpath "/gmd:description/gco:CharacterString")))

(defmulti umm-spec-shape
  "Returns a UMM-spec shape map from a CMR spatial lib shape type. Dispatches on type of argument."
  (fn [umm-spatial-shape]
    (type umm-spatial-shape)))

(defmethod umm-spec-shape cmr.spatial.point.Point
  [p]
  {:Longitude (:lon p)
   :Latitude  (:lat p)})

(defmethod umm-spec-shape cmr.spatial.mbr.Mbr
  [mbr]
  {:WestBoundingCoordinate  (:west mbr)
   :EastBoundingCoordinate  (:east mbr)
   :NorthBoundingCoordinate (:north mbr)
   :SouthBoundingCoordinate (:south mbr)})

(defmethod umm-spec-shape cmr.spatial.line_string.LineString
  [line]
  {:Points (map umm-spec-shape (:points line))})

(defmethod umm-spec-shape cmr.spatial.polygon.Polygon
  [poly]
  (let [[boundary-ring & interior-rings] (:rings poly)]
    {:Boundary {:Points (map umm-spec-shape (:points boundary-ring))}
     :ExclusiveZone {:Boundaries (for [r interior-rings]
                                   {:Points (map umm-spec-shape (:points r))})}}))

(defn- shape-el?
  "Returns true if XML element is (probably) a shape instead of other geographic information elements."
  [el]
  (empty? (select el "gmd:EX_GeographicDescription")))

(defn parse-geometry
  "Returns UMM GeometryType map from ISO XML document."
  [doc]
  (let [geo-elems      (filter shape-el? (select doc geographic-element-xpath))
        ;; ISO includes bounding boxes for each element (point, polygon, etc.) in the spatial extent
        ;; metadata. We can discard the redundant bounding boxes.
        shape-elems    (map second (partition 2 geo-elems))
        shapes         (keep gmd/decode shape-elems)
        shapes-by-type (group-by #(.getName (class %)) shapes)
        get-shapes     (fn [k]
                         (map umm-spec-shape (get shapes-by-type k)))]
    {:CoordinateSystem   (value-of doc coordinate-system-xpath)
     :Points             (get-shapes "cmr.spatial.point.Point")
     :BoundingRectangles (get-shapes "cmr.spatial.mbr.Mbr")
     :Lines              (get-shapes "cmr.spatial.line_string.LineString")
     :GPolygons          (get-shapes "cmr.spatial.polygon.Polygon")}))

(defn- parse-orbit-parameters
  "Parses orbit parameters from the ISO XML document. Orbit parameters are encoded in an ISO XML
  document as a single string like this:
  \"SwathWidth: 2.0 Period: 96.7 InclinationAngle: 94.0 NumberOfOrbits: 2.0 StartCircularLatitude: 50.0\""
  [doc]
  (when-let [orbit-string (value-of doc orbit-string-xpath)]
    (into {} (for [[k ^String v] (partition 2 (str/split orbit-string #":? "))]
               [(keyword k) (Double/parseDouble v)]))))


(defn parse-spatial
  "Returns UMM SpatialExtentType map from ISO XML document."
  [doc extent-info]
  {:SpatialCoverageType (get extent-info "SpatialCoverageType")
   :GranuleSpatialRepresentation (get extent-info "SpatialGranuleSpatialRepresentation")
   :HorizontalSpatialDomain {:Geometry (parse-geometry doc)}
   :VerticalSpatialDomains (let [vsd-type  (get extent-info "VerticalSpatialDomainType")
                                 vsd-value (get extent-info "VerticalSpatialDomainValue")]
                             (when (or vsd-type vsd-value)
                               [{:Type vsd-type
                                 :Value vsd-value}]))
   :OrbitParameters (parse-orbit-parameters doc)})
