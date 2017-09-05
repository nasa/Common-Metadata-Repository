(ns cmr.umm-spec.xml-to-umm-mappings.echo10.spatial
  "Defines mappings from ECHO10 XML spatial elements into UMM records"
  (:require
   [camel-snake-kebab.internals.misc :as csk-misc]
   [clojure.string :as string]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select text]]
   [cmr.umm-spec.spatial-conversion :as spatial-conversion]
   [cmr.umm-spec.util :as u]))

(defn- parse-point
  [el]
  (when el
    {:Longitude (Double. (value-of el "PointLongitude"))
     :Latitude  (Double. (value-of el "PointLatitude"))}))

(defn- parse-center-point-of
  [el]
  (parse-point (first (select el "CenterPoint"))))

(defn- parse-line
  [el]
  {:CenterPoint (parse-center-point-of el)
   :Points (map parse-point (select el "Point"))})

(defn- parse-polygon
  [el]
  {:CenterPoint (parse-center-point-of el)
   :Boundary {:Points (u/open-clockwise->closed-counter-clockwise (map parse-point (select el "Boundary/Point")))}
   :ExclusiveZone {:Boundaries (for [boundary (select el "ExclusiveZone/Boundary")]
                                 {:Points (u/open-clockwise->closed-counter-clockwise (map parse-point (select boundary "Point")))})}})

(defn- parse-bounding-rect
  [el]
  (merge {:CenterPoint (parse-center-point-of el)}
         (fields-from el :WestBoundingCoordinate :NorthBoundingCoordinate
                      :EastBoundingCoordinate :SouthBoundingCoordinate)))

(defn- parse-geometry
  [geometry-element]
  {:CoordinateSystem   (value-of geometry-element "CoordinateSystem")
   :Points             (map parse-point (select geometry-element "Point"))
   :BoundingRectangles (map parse-bounding-rect (select geometry-element "BoundingRectangle"))
   :GPolygons          (map parse-polygon (select geometry-element "GPolygon"))
   :Lines              (map parse-line (select geometry-element "Line"))})

(defn parse-spatial
  "Returns UMM-C spatial map from ECHO10 XML document."
  [doc sanitize?]
  (if-let [[spatial] (select doc "/Collection/Spatial")]
    {:SpatialCoverageType          (when-let [sct (value-of spatial "SpatialCoverageType")]
                                     (string/upper-case sct))
     :GranuleSpatialRepresentation (value-of spatial "GranuleSpatialRepresentation")
     :HorizontalSpatialDomain      (let [[horiz] (select spatial "HorizontalSpatialDomain")]
                                     {:ZoneIdentifier (value-of horiz "ZoneIdentifier")
                                      :Geometry       (parse-geometry (first (select horiz "Geometry")))})
     :VerticalSpatialDomains       (spatial-conversion/convert-vertical-spatial-domains-from-xml
                                    (select spatial "VerticalSpatialDomain"))
     :OrbitParameters              (fields-from (first (select spatial "OrbitParameters"))
                                                :SwathWidth
                                                :Period
                                                :InclinationAngle
                                                :NumberOfOrbits
                                                :StartCircularLatitude)}
    (when sanitize?
      u/not-provided-spatial-extent)))
