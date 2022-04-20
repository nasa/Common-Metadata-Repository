(ns cmr.umm-spec.umm-to-xml-mappings.dif10.spatial
  (:require
   [camel-snake-kebab.core :as csk]
   [cmr.common.xml.gen :refer :all]
   [cmr.umm-spec.migration.version.collection :as version-collection]
   [cmr.umm-spec.util :as u]))

;; CMR-1990 - We need to consolidate the SpatialCoverageTypeEnum between UMM JSON and DIF10
(def umm-spatial-type->dif10-spatial-type
  {"HORIZONTAL" "Horizontal"
   "VERTICAL" "Vertical"
   "ORBITAL" "Orbit"
   "HORIZONTAL_VERTICAL" "HorizontalVertical"
   "ORBITAL_VERTICAL" "HorizontalVertical"
   "HORIZONTAL_ORBITAL" "Orbit"
   "HORIZONTAL_VERTICAL_ORBITAL" "Orbit"})

(defn- point-contents
  "Returns the inner lon/lat elements for a DIF Point element from a UMM PointType record."
  [point]
  (list [:Point_Longitude (:Longitude point)]
        [:Point_Latitude  (:Latitude  point)]))

(defn- point-element
  "Returns DIF Point element from a given UMM PointType record."
  [point]
  [:Point
   (point-contents point)])

(defn- bounding-rect-element
  "Returns DIF 10 Bounding_Rectangle element from a UMM BoundingRectangleType record."
  [rect]
  [:Bounding_Rectangle
   [:Southernmost_Latitude (:SouthBoundingCoordinate rect)]
   [:Northernmost_Latitude (:NorthBoundingCoordinate rect)]
   [:Westernmost_Longitude (:WestBoundingCoordinate rect)]
   [:Easternmost_Longitude (:EastBoundingCoordinate rect)]])

(defn- polygon-element
  "Returns DIF 10 Polygon element from UMM GPolygonType record."
  [poly]
  [:Polygon
   [:Boundary
    (map point-element (u/closed-counter-clockwise->open-clockwise (-> poly :Boundary :Points)))]
   [:Exclusive_Zone
    (for [b (-> poly :ExclusiveZone :Boundaries)]
      [:Boundary
       (map point-element (u/closed-counter-clockwise->open-clockwise (:Points b)))])]])

(defn- line-element
  "Returns DIF 10 Line element from given UMM LineType record."
  [line]
  [:Line
   (map point-element (:Points line))])

(defn- tiling-system-coord-element
  "Returns a DIF 10 tiling system Coordinate(n) element."
  [tiling-sys k]
  (let [coord (get tiling-sys k)]
    ;; the element will have the same tag name as the key (Coordinate1 or Coordinate2)
    [k
     [:Minimum_Value (:MinimumValue coord)]
     [:Maximum_Value (:MaximumValue coord)]]))

(defn- convert-vertical-spatial-domains
  "Validate and convert vertical spatial domains to dif10"
  [vertical-spatial-domains]
  (map
   (fn [spatial-domain]
    (update spatial-domain :Type #(csk/->Camel_Snake_Case %)))
   vertical-spatial-domains))

(defn- convert-horizontal-data-resolutions
  "Converts UMM Spatial Extent values to DIF10 Geographic_Coordinate_System"
  [spatial-extent]
  (when-let [horizontal-data-resolutions (get-in spatial-extent [:HorizontalSpatialDomain :ResolutionAndCoordinateSystem :HorizontalDataResolution])]
    (let [;; get the first entry in the resolution groups that contains :XDimention or :YDimension.
          horizontal-data-resolution (or (first (:NonGriddedResolutions horizontal-data-resolutions))
                                         (first (:GriddedResolutions horizontal-data-resolutions))
                                         (first (:GenericResolutions horizontal-data-resolutions)))
          x (:XDimension horizontal-data-resolution)
          y (:YDimension horizontal-data-resolution)]
      (when (or x y)
        [:Geographic_Coordinate_System
         [:GeographicCoordinateUnits (:Unit horizontal-data-resolution)]
         [:LatitudeResolution (:YDimension horizontal-data-resolution)]
         [:LongitudeResolution (:XDimension horizontal-data-resolution)]]))))

(defn- convert-geodetic-model
  "Converts UMM Horizontal Coordinate System GeodeticModel to DIF10 GeodeticModel."
  [spatial-extent]
  (when-let [geodetic-model (get-in spatial-extent [:HorizontalSpatialDomain :ResolutionAndCoordinateSystem :GeodeticModel])]
    (let [datum-name (get geodetic-model :HorizontalDatumName)
          ellipsoid-name (get geodetic-model :EllipsoidName)
          semi-major-axis (get geodetic-model :SemiMajorAxis)
          denominator-of-flattening-ratio (get geodetic-model :DenominatorOfFlatteningRatio)]
      [:Geodetic_Model
       [:Horizontal_DatumName datum-name]
       [:Ellipsoid_Name ellipsoid-name]
       [:Semi_Major_Axis semi-major-axis]
       [:Denominator_Of_Flattening_Ratio denominator-of-flattening-ratio]])))

(defn- convert-local-coordinate-sys
  "Converts UMM Horizontal Coordinate System GeodeticModel to DIF10 GeodeticModel."
  [spatial-extent]
  (when-let [local-coordinate-sys (get-in spatial-extent [:HorizontalSpatialDomain :ResolutionAndCoordinateSystem :LocalCoordinateSystem])]
    (let [geo-reference-information (get local-coordinate-sys :GeoReferenceInformation)
          description (get local-coordinate-sys :Description)]
      [:Local_Coordinate_System
       [:Description description]
       [:GeoReference_Information geo-reference-information]])))

(defn spatial-element
  "Returns DIF10 Spatial_Coverage element from given UMM-C record."
  [c]
  (if-let [sp (:SpatialExtent c)]
    [:Spatial_Coverage
     [:Spatial_Coverage_Type (umm-spatial-type->dif10-spatial-type (:SpatialCoverageType sp))]
     [:Granule_Spatial_Representation (or (:GranuleSpatialRepresentation sp)
                                          u/default-granule-spatial-representation)]
     [:Zone_Identifier (-> sp :HorizontalSpatialDomain :ZoneIdentifier)]
     (let [geom (-> sp :HorizontalSpatialDomain :Geometry)]
       [:Geometry
        [:Coordinate_System (u/coordinate-system geom)]
        (concat
          ;; From most-specific to least specific. This is arbitrary.
          (map polygon-element (:GPolygons geom))
          (map bounding-rect-element (:BoundingRectangles geom))
          (map line-element (:Lines geom))
          (map point-element (:Points geom)))])
     (let [o (:OrbitParameters sp)]
       [:Orbit_Parameters
        ;; convert the SwathWidth value or get the largest Footprint in assumed unit.
        [:Swath_Width (version-collection/get-swath-width c)]
        [:Period (:OrbitPeriod o)]
        [:Inclination_Angle (:InclinationAngle o)]
        [:Number_Of_Orbits (:NumberOfOrbits o)]
        [:Start_Circular_Latitude (:StartCircularLatitude o)]])
     (for [vert (convert-vertical-spatial-domains (:VerticalSpatialDomains sp))]
       [:Vertical_Spatial_Info
        (elements-from vert :Type :Value)])
     [:Spatial_Info
      [:Spatial_Coverage_Type u/not-provided]
      [:Horizontal_Coordinate_System
       (convert-geodetic-model sp)
       (convert-horizontal-data-resolutions sp)
       (convert-local-coordinate-sys sp)]
      (for [sys (:TilingIdentificationSystems c)]
        [:TwoD_Coordinate_System
         [:TwoD_Coordinate_System_Name (:TilingIdentificationSystemName sys)]
         (tiling-system-coord-element sys :Coordinate1)
         (tiling-system-coord-element sys :Coordinate2)])]]

    ;; Default Spatial_Coverage
    [:Spatial_Coverage
     [:Spatial_Coverage_Type "Horizontal"]
     [:Granule_Spatial_Representation u/default-granule-spatial-representation]]))
