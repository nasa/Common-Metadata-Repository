(ns cmr.umm-spec.xml-to-umm-mappings.dif10.spatial
  "Defines mappings from DIF 10 XML spatial elements into UMM records"
  (:require
   [clojure.string :as string]
   [cmr.common.util :as util]
   [cmr.common.xml.parse :refer [value-of]]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.umm-spec.spatial-conversion :as spatial-conversion]
   [cmr.umm-spec.models.umm-collection-models :as umm-c]
   [cmr.umm-spec.util :as u]))

(def dif10-spatial-type->umm-spatial-type
  {"Horizontal" "HORIZONTAL"
   "Vertical" "VERTICAL"
   "Orbit" "ORBITAL"
   "HorizontalVertical" "HORIZONTAL_VERTICAL"
   "Horizon&amp;Vert" "HORIZONTAL_VERTICAL"
   "Orbital_Vertical" "ORBITAL_VERTICAL"
   "Horizontal_Orbital" "HORIZONTAL_ORBITAL"
   "Horizontal_Vertical_Orbital" "HORIZONTAL_VERTICAL_ORBITAL"
   "Lunar" "LUNAR"
   "Earth/Global" "EARTH/GLOBAL"})

(defn- parse-point
  [el]
  (when el
    {:Longitude (Double/parseDouble (value-of el "Point_Longitude"))
     :Latitude  (Double/parseDouble (value-of el "Point_Latitude"))}))

(defn- parse-line
  [el]
  {:Points (map parse-point (select el "Point"))})

(defn- parse-polygon
  [el]
  {:Boundary {:Points (u/open-clockwise->closed-counter-clockwise (map parse-point (select el "Boundary/Point")))}
   :ExclusiveZone {:Boundaries (for [boundary (select el "Exclusive_Zone/Boundary")]
                                 {:Points (u/open-clockwise->closed-counter-clockwise
                                            (map parse-point (select boundary "Point")))})}})

(defn- parse-bounding-rect
  [el]
  (merge {:NorthBoundingCoordinate (value-of el "Northernmost_Latitude")
          :SouthBoundingCoordinate (value-of el "Southernmost_Latitude")
          :EastBoundingCoordinate (value-of el "Easternmost_Longitude")
          :WestBoundingCoordinate (value-of el "Westernmost_Longitude")}))

(defn- parse-geometry
  [g]
  {:CoordinateSystem   (value-of g "Coordinate_System")
   :Points             (map parse-point (select g "Point"))
   :BoundingRectangles (map parse-bounding-rect (select g "Bounding_Rectangle"))
   :GPolygons          (map parse-polygon (select g "Polygon"))
   :Lines              (map parse-line (select g "Line"))})

(defn- parse-data-resolution
  "Parses Data_Resolution element into HorizontalDataResolution."
  [data-resolution]
  (let [x (u/parse-dimension (value-of data-resolution "Latitude_Resolution"))
        y (u/parse-dimension (value-of data-resolution "Longitude_Resolution"))
        unit (or (u/guess-units (value-of data-resolution "Longitude_Resolution")) u/not-provided)]
    (umm-c/map->HorizontalDataResolutionType
      {:GenericResolutions (u/remove-empty-records
                             [(umm-c/map->HorizontalDataGenericResolutionType
                                (util/remove-nil-keys
                                  {:YDimension x
                                   :XDimension y
                                   :Unit unit}))])})))

(defn- parse-horizontal-data-resolutions
  "Parses the dif10 elements needed to populate HorizontalDataResolution.
   Uses Horizontal_Coordinate_System under Geographic_Coordinate_System (LatitudeResolution and LongitudeResolution),
   Otherwise uses  Data_Resolution. When looking at Data_Resolution though there can be more than 1,
   so for each one that includes a Latitude and Longitude Resolution adds a new Horizontal Resolution."
  [doc]
  (let [[geo-coor-sys] (select doc "/DIF/Spatial_Coverage/Spatial_Info/Horizontal_Coordinate_System/Geographic_Coordinate_System")
        [data-res] (select doc "/DIF/Data_Resolution")]
    (when (or geo-coor-sys data-res)
      (if geo-coor-sys
        (umm-c/map->HorizontalDataResolutionType
          {:GenericResolutions (u/remove-empty-records
                                 [(umm-c/map->HorizontalDataGenericResolutionType
                                    (util/remove-nil-keys
                                      {:YDimension (when-let [y (value-of geo-coor-sys "LatitudeResolution")]
                                                     (read-string y))
                                       :XDimension (when-let [x (value-of geo-coor-sys "LongitudeResolution")]
                                                     (read-string x))
                                       :Unit (value-of geo-coor-sys "GeographicCoordinateUnits")}))])})
        (parse-data-resolution data-res)))))

(defn- parse-local-coord-system
  "Parses the dif10 elements needed for LocalCoordinateSystem values."
  [spatial-coverage]
  (when-let [[local-coordinate-sys] (select spatial-coverage (str "Spatial_Info/Horizontal_Coordinate_System/"
                                                                  "Local_Coordinate_System"))]
    (umm-c/map->LocalCoordinateSystemType
      (util/remove-nil-keys
        {:Description (value-of local-coordinate-sys "Description")
         :GeoReferenceInformation (value-of local-coordinate-sys "GeoReference_Information")}))))

(defn- parse-geodetic-model
  "Parses the dif10 elements needed for GeodeticModel values."
  [spatial-coverage]
  (when-let [[geodetic-model] (select spatial-coverage (str "Spatial_Info/Horizontal_Coordinate_System/"
                                                            "Geodetic_Model"))]
    (umm-c/map->GeodeticModelType
     (util/remove-nil-keys
      {:HorizontalDatumName (value-of geodetic-model "Horizontal_DatumName")
       :EllipsoidName (value-of geodetic-model "Ellipsoid_Name")
       :SemiMajorAxis (when-let [axis (value-of geodetic-model "Semi_Major_Axis")]
                        (read-string axis))
       :DenominatorOfFlatteningRatio (when-let [denom (value-of geodetic-model "Denominator_Of_Flattening_Ratio")]
                                       (read-string denom))}))))

(defn- parse-horizontal-spatial-domains
  "Parses the dif10 elements needed for HorizontalSpatialDomain values."
  [doc]
  (let [[spatial-coverage] (select doc "/DIF/Spatial_Coverage")
        [geom] (select spatial-coverage "Geometry")
        horizontal-data-resolutions (parse-horizontal-data-resolutions doc)
        local-coordinate-sys (parse-local-coord-system spatial-coverage)
        geodetic-model (parse-geodetic-model spatial-coverage)
        resolution (if (or geodetic-model
                           local-coordinate-sys
                           horizontal-data-resolutions)
                     {:ResolutionAndCoordinateSystem {:GeodeticModel geodetic-model
                                                      :LocalCoordinateSystem local-coordinate-sys
                                                      :HorizontalDataResolution horizontal-data-resolutions}}
                     {:ResolutionAndCoordinateSystem nil})]
    (util/remove-nil-keys
     (merge
      {:Geometry (parse-geometry geom)
       :ZoneIdentifier (value-of spatial-coverage "Zone_Identifier")}
       resolution))))

(defn parse-spatial
  "Returns UMM-C spatial map from DIF 10 XML document."
  [doc]
  (let [[spatial] (select doc "/DIF/Spatial_Coverage")
        [orbit-parameters] (select spatial "Orbit_Parameters")]
      {:SpatialCoverageType (dif10-spatial-type->umm-spatial-type (value-of spatial "Spatial_Coverage_Type"))
       :GranuleSpatialRepresentation (value-of spatial "Granule_Spatial_Representation")
       :HorizontalSpatialDomain      (parse-horizontal-spatial-domains doc)
       :VerticalSpatialDomains       (spatial-conversion/convert-vertical-spatial-domains-from-xml
                                      (select spatial "Vertical_Spatial_Info"))
       :OrbitParameters              (when orbit-parameters
                                       (as->{:SwathWidth (util/safe-read-string (value-of orbit-parameters "Swath_Width"))
                                             :OrbitPeriod (util/safe-read-string (value-of orbit-parameters "Period"))
                                             :InclinationAngle (util/safe-read-string (value-of orbit-parameters "Inclination_Angle"))
                                             :NumberOfOrbits (util/safe-read-string (value-of orbit-parameters "Number_Of_Orbits"))
                                             :StartCircularLatitude (util/safe-read-string (value-of orbit-parameters "Start_Circular_Latitude"))} op
                                             ;; Add assumed units for the corresponding fields.
                                             (if (:SwathWidth op)
                                               (assoc op :SwathWidthUnit "Kilometer")
                                               op)
                                             (if (:OrbitPeriod op)
                                               (assoc op :OrbitPeriodUnit "Decimal Minute")
                                               op)
                                             (if (:InclinationAngle op)
                                               (assoc op :InclinationAngleUnit "Degree")
                                               op)
                                             (if (:StartCircularLatitude op)
                                               (assoc op :StartCircularLatitudeUnit "Degree")
                                               op)))}))

(def tiling-system-xpath
  "/DIF/Spatial_Coverage/Spatial_Info/TwoD_Coordinate_System")

(defn- parse-tiling-coord
  [twod-el coord-xpath tiling-id-name]
  (when-let [[coord-el] (select twod-el coord-xpath)]
    (if (string/includes? tiling-id-name "Military Grid Reference System")
      (umm-c/map->TilingCoordinateType
       (util/remove-nil-keys
        {:MinimumValue (value-of coord-el "Minimum_Value")
         :MaximumValue (value-of coord-el "Maximum_Value")}))
      (umm-c/map->TilingCoordinateNumericType
       (util/remove-nil-keys
        {:MinimumValue (util/str->num (value-of coord-el "Minimum_Value"))
         :MaximumValue (util/str->num (value-of coord-el "Maximum_Value"))})))))

(defn parse-tiling
  "Returns UMM-C TilingIdentificationSystem map from DIF 10 XML document."
  [doc]
  (for [twod-el (select doc tiling-system-xpath)]
    (let [tiling-id-system-name (spatial-conversion/translate-tile-id-system-name
                                  (value-of twod-el "TwoD_Coordinate_System_Name"))]
     (when tiling-id-system-name
      {:TilingIdentificationSystemName tiling-id-system-name
       :Coordinate1 (parse-tiling-coord twod-el "Coordinate1" tiling-id-system-name)
       :Coordinate2 (parse-tiling-coord twod-el "Coordinate2" tiling-id-system-name)}))))
