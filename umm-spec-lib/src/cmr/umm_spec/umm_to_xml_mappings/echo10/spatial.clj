(ns cmr.umm-spec.umm-to-xml-mappings.echo10.spatial
  (:require [cmr.umm-spec.xml.gen :refer :all]
            [cmr.umm-spec.util :as u]))

(defn echo-point-order
  "Returns a sequence of points in ECHO order (open and clockwise)."
  [points]
  (reverse (butlast points)))

(defn- point-contents
  "Returns the inner lon/lat elements for an ECHO Point or CenterPoint element from a UMM PointType
  record."
  [point]
  (list [:PointLongitude (:Longitude point)]
        [:PointLatitude  (:Latitude  point)]))

(defn- point-element
  "Returns ECHO Point element from a given UMM PointType record."
  [point]
  [:Point
   (point-contents point)])

(defn- center-point-of
  "Returns ECHO CenterPoint element from the :CenterPoint of the given record."
  [x]
  [:CenterPoint (point-contents (:CenterPoint x))])

(defn- bounding-rect-element
  "Returns ECHO BoundingRectangle element from a UMM BoundingRectangleType record."
  [rect]
  [:BoundingRectangle
   (elements-from rect
                  :WestBoundingCoordinate :NorthBoundingCoordinate
                  :EastBoundingCoordinate :SouthBoundingCoordinate)
   (center-point-of rect)])

(defn- polygon-element
  "Returns ECHO GPolygon element from UMM GPolygonType record."
  [poly]
  [:GPolygon
   [:Boundary
    (map point-element (echo-point-order (-> poly :Boundary :Points)))]
   [:ExclusiveZone
    (for [b (-> poly :ExclusiveZone :Boundaries)]
      [:Boundary
       (map point-element  (echo-point-order (:Points b)))])]
   [:CenterPoint
    (point-contents (:CenterPoint poly))]])

(defn- line-element
  "Returns ECHO Line element from given UMM LineType record."
  [line]
  [:Line
   (map point-element (:Points line))
   (center-point-of line)])

(defn- coordinate-system
  "Returns the CoordinateSystem of the given geometry."
  [geom]
  (let [{:keys [CoordinateSystem GPolygons BoundingRectangles Lines Points]} geom]
  (if (and (nil? (:CoordinateSystem geom))
           (or GPolygons BoundingRectangles Lines Points))
    u/default-granule-spatial-representation
    (:CoordinateSystem geom))))

(defn spatial-element
  "Returns ECHO10 Spatial element from given UMM-C record."
  [c]
  (let [sp (:SpatialExtent c)]
    [:Spatial
     (elements-from sp :SpatialCoverageType)
     (let [horiz (:HorizontalSpatialDomain sp)]
       [:HorizontalSpatialDomain
        (elements-from horiz :ZoneIdentifier)
        (let [geom (:Geometry horiz)]
          [:Geometry
           [:CoordinateSystem (coordinate-system geom)]
           (map point-element (:Points geom))
           (map bounding-rect-element (:BoundingRectangles geom))
           (map polygon-element (:GPolygons geom))
           (map line-element (:Lines geom))])])
     (for [vert (:VerticalSpatialDomains sp)]
       [:VerticalSpatialDomain
        (elements-from vert :Type :Value)])
     [:OrbitParameters
      (elements-from (:OrbitParameters sp)
                     :SwathWidth :Period :InclinationAngle
                     :NumberOfOrbits :StartCircularLatitude)]
     [:GranuleSpatialRepresentation (or (:GranuleSpatialRepresentation sp)
                                        u/default-granule-spatial-representation)]]))
