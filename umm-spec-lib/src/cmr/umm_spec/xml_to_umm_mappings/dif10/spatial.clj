(ns cmr.umm-spec.xml-to-umm-mappings.dif10.spatial
  "Defines mappings from DIF 10 XML spatial elements into UMM records"
  (:require [clojure.set :as set]
            [cmr.umm-spec.simple-xpath :refer [select text]]
            [cmr.umm-spec.xml.parse :refer :all]
            [cmr.umm-spec.umm-to-xml-mappings.dif10.spatial :as utx]))

(def dif10-spatial-type->umm-spatial-type
  (set/map-invert utx/umm-spatial-type->dif10-spatial-type))

(defn- parse-point
  [el]
  (when el
    {:Longitude (Double. (value-of el "Point_Longitude"))
     :Latitude  (Double. (value-of el "Point_Latitude"))}))

(defn- parse-center-point-of
  [el]
  (parse-point (first (select el "Center_Point"))))

(defn- parse-line
  [el]
  ;; DIF 10's Line uses CenterPoint where other shapes use Center_Point
  {:CenterPoint (parse-point (first (select el "CenterPoint")))
   :Points (map parse-point (select el "Point"))})

(defn- parse-polygon
  [el]
  {:CenterPoint (parse-center-point-of el)
   :Boundary {:Points (map parse-point (select el "Boundary/Point"))}
   :ExclusiveZone {:Boundaries (for [boundary (select el "Exclusive_Zone/Boundary")]
                                 {:Points (map parse-point (select boundary "Point"))})}})

(defn- parse-bounding-rect
  [el]
  (merge {:CenterPoint (parse-center-point-of el)
          :NorthBoundingCoordinate (value-of el "Northernmost_Latitude")
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

(defn parse-spatial
  "Returns UMM-C spatial map from DIF 10 XML document."
  [doc]
  (let [[spatial] (select doc "/DIF/Spatial_Coverage")]
    {:SpatialCoverageType (dif10-spatial-type->umm-spatial-type (value-of spatial "Spatial_Coverage_Type"))
     :GranuleSpatialRepresentation (value-of spatial "Granule_Spatial_Representation")
     :HorizontalSpatialDomain      (let [[geom] (select spatial "Geometry")]
                                     {:Geometry (parse-geometry geom)
                                      :ZoneIdentifier (value-of spatial "Zone_Identifier")})
     :VerticalSpatialDomains       (for [vert-elem (select spatial "Vertical_Spatial_Info")]
                                     (fields-from vert-elem :Type :Value))
     :OrbitParameters              (let [[o] (select spatial "Orbit_Parameters")]
                                     {:SwathWidth (value-of o "Swath_Width")
                                      :Period (value-of o "Period")
                                      :InclinationAngle (value-of o "Inclination_Angle")
                                      :NumberOfOrbits (value-of o "Number_Of_Orbits")
                                      :StartCircularLatitude (value-of o "Start_Circular_Latitude")})}))

(def tiling-system-xpath
  "/DIF/Spatial_Coverage/Spatial_Info/TwoD_Coordinate_System")

(defn- parse-tiling-coord
  [twod-el coord-xpath]
  (when-let [[coord-el] (select twod-el coord-xpath)]
    {:MinimumValue (value-of coord-el "Minimum_Value")
     :MaximumValue (value-of coord-el "Maximum_Value")}))

(defn parse-tiling
  "Returns UMM-C TilingIdentificationSystem map from DIF 10 XML document."
  [doc]
  (when-let [[twod-el] (select doc tiling-system-xpath)]
    {:TilingIdentificationSystemName (value-of twod-el "TwoD_Coordinate_System_Name")
     :Coordinate1 (parse-tiling-coord twod-el "Coordinate1")
     :Coordinate2 (parse-tiling-coord twod-el "Coordinate2")}))
