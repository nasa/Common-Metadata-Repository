(ns cmr.umm-spec.xml-to-umm-mappings.echo10.spatial
  "Defines mappings from ECHO10 XML spatial elements into UMM records"
  (:require
   [camel-snake-kebab.internals.misc :as csk-misc]
   [clojure.string :as string]
   [cmr.common.util :as util]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select text]]
   [cmr.umm-spec.models.umm-collection-models :as umm-c]
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

(defn- parse-horizontal-data-resolutions
  "Parses the ECHO10 elements needed for HorizontalDataResolution values."
  [spatial-info]
  (when-let [[geo-coor-sys] (select spatial-info "HorizontalCoordinateSystem/GeographicCoordinateSystem")]
    (umm-c/map->HorizontalDataResolutionType
      {:GenericResolutions (u/remove-empty-records
                             [(umm-c/map->HorizontalDataGenericResolutionType
                               (util/remove-nil-keys
                                 {:YDimension (when-let [y (value-of geo-coor-sys "LatitudeResolution")]
                                                (read-string y))
                                  :XDimension (when-let [x (value-of geo-coor-sys "LongitudeResolution")]
                                                (read-string x))
                                  :Unit (value-of geo-coor-sys "GeographicCoordinateUnits")}))])})))

(defn- parse-local-coord-system
  "Parses the ECHO10 elements needed for LocalCoordinateSystem values."
  [spatial-info]
  (when-let [[local-coordinate-sys] (select spatial-info "HorizontalCoordinateSystem/LocalCoordinateSystem")]
    (umm-c/map->LocalCoordinateSystemType
      (util/remove-nil-keys
        {:Description (value-of local-coordinate-sys "Description")
         :GeoReferenceInformation (value-of local-coordinate-sys "GeoReferenceInformation")}))))

(defn- parse-geodetic-model
  "Parses the ECHO10 elements needed for GeodeticModel values."
  [spatial-info]
  (when-let [[geodetic-model] (select spatial-info "HorizontalCoordinateSystem/GeodeticModel")]
    (umm-c/map->GeodeticModelType
     (util/remove-nil-keys
      {:HorizontalDatumName (value-of geodetic-model "HorizontalDatumName")
       :EllipsoidName (value-of geodetic-model "EllipsoidName")
       :SemiMajorAxis (when-let [axis (value-of geodetic-model "SemiMajorAxis")]
                        (read-string axis))
       :DenominatorOfFlatteningRatio (when-let [denom (value-of geodetic-model "DenominatorOfFlatteningRatio")]
                                       (read-string denom))}))))

(defn- parse-horizontal-spatial-domain
  "Parses the ECHO10 elements needed for HorizontalSpatialDomain values."
  [doc]
  (let [[spatial-info] (select doc "/Collection/SpatialInfo")
        horizontal-data-resolutions (parse-horizontal-data-resolutions spatial-info)
        local-coordinate-sys (parse-local-coord-system spatial-info)
        geodetic-model (parse-geodetic-model spatial-info)
        [horiz] (select doc "/Collection/Spatial/HorizontalSpatialDomain")]
    (umm-c/map->HorizontalSpatialDomainType
     (util/remove-nil-keys
      {:Geometry (parse-geometry (first (select horiz "Geometry")))
       :ZoneIdentifier (value-of horiz "ZoneIdentifier")
       :ResolutionAndCoordinateSystem {:GeodeticModel geodetic-model
                                       :LocalCoordinateSystem local-coordinate-sys
                                       :HorizontalDataResolution horizontal-data-resolutions}}))))

(defn parse-spatial
  "Returns UMM-C spatial map from ECHO10 XML document."
  [doc sanitize?]
  (if-let [[spatial] (select doc "/Collection/Spatial")]
    {:SpatialCoverageType          (when-let [sct (value-of spatial "SpatialCoverageType")]
                                     (string/upper-case sct))
     :GranuleSpatialRepresentation (value-of spatial "GranuleSpatialRepresentation")
     :HorizontalSpatialDomain      (parse-horizontal-spatial-domain doc)
     :VerticalSpatialDomains       (spatial-conversion/convert-vertical-spatial-domains-from-xml
                                    (select spatial "VerticalSpatialDomain"))
     :OrbitParameters              (as->(fields-from (first (select spatial "OrbitParameters"))
                                                     :SwathWidth
                                                     :Period
                                                     :InclinationAngle
                                                     :NumberOfOrbits
                                                     :StartCircularLatitude) op
                                        ;; Add assumed units for the corresponding fields.
                                        ;; Replace :Period with :OrbitPeriod.
                                        (if (:SwathWidth op) 
                                          (assoc op :SwathWidthUnit "Kilometer")
                                          op)
                                        (if (:Period op) 
                                          (assoc op :OrbitPeriod (:Period op)
                                                    :OrbitPeriodUnit "Decimal Minute")
                                          op)
                                        (if (:InclinationAngle op)
                                          (assoc op :InclinationAngleUnit "Degree")
                                          op)
                                        (if (:StartCircularLatitude op)
                                          (assoc op :StartCircularLatitudeUnit "Degree")
                                          op)
                                        (dissoc op :Period))}
    (when sanitize?
      u/not-provided-spatial-extent)))
