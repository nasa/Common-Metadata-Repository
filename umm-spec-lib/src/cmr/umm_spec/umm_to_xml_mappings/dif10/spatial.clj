(ns cmr.umm-spec.umm-to-xml-mappings.dif10.spatial
  (:require [cmr.common.xml.gen :refer :all]
            [cmr.umm-spec.util :as u]))

;; CMR-1990 - We need to consolidate the SpatialCoverageTypeEnum between UMM JSON and DIF10
(def umm-spatial-type->dif10-spatial-type
  {"HORIZONTAL" "Horizontal"
   "VERTICAL" "Vertical"
   "ORBITAL" "Orbit"
   "HORIZONTAL_VERTICAL" "HorizontalVertical"
   "ORBITAL_VERTICAL" "HorizontalVertical"})

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
        [:Swath_Width (:SwathWidth o)]
        [:Period (:Period o)]
        [:Inclination_Angle (:InclinationAngle o)]
        [:Number_Of_Orbits (:NumberOfOrbits o)]
        [:Start_Circular_Latitude (:StartCircularLatitude o)]])
     (for [vert (:VerticalSpatialDomains sp)]
       [:Vertical_Spatial_Info
        (elements-from vert :Type :Value)])
     [:Spatial_Info
      [:Spatial_Coverage_Type u/not-provided]
      (for [sys (:TilingIdentificationSystems c)]
        [:TwoD_Coordinate_System
         [:TwoD_Coordinate_System_Name (:TilingIdentificationSystemName sys)]
         (tiling-system-coord-element sys :Coordinate1)
         (tiling-system-coord-element sys :Coordinate2)])]]

    ;; Default Spatial_Coverage
    [:Spatial_Coverage
     [:Spatial_Coverage_Type "Horizontal"]
     [:Granule_Spatial_Representation u/default-granule-spatial-representation]]))
