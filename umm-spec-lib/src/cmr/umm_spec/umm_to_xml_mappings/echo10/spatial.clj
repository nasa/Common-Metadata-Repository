(ns cmr.umm-spec.umm-to-xml-mappings.echo10.spatial
  (:require
   [cmr.common.xml.gen :refer :all]
   [cmr.umm-spec.migration.version.collection :as version-collection]
   [cmr.umm-spec.spatial-conversion :as spatial-conversion]
   [cmr.umm-spec.util :as u]))

(defn- point-contents
  "Returns the inner lon/lat elements for an ECHO Point from a UMM PointType
  record."
  [point]
  (list [:PointLongitude (:Longitude point)]
        [:PointLatitude  (:Latitude  point)]))

(defn- point-element
  "Returns ECHO Point element from a given UMM PointType record."
  [point]
  [:Point
   (point-contents point)])

(defn- bounding-rect-element
  "Returns ECHO BoundingRectangle element from a UMM BoundingRectangleType record."
  [rect]
  [:BoundingRectangle
   (elements-from rect
                  :WestBoundingCoordinate :NorthBoundingCoordinate
                  :EastBoundingCoordinate :SouthBoundingCoordinate)])

(defn- polygon-element
  "Returns ECHO GPolygon element from UMM GPolygonType record."
  [poly]
  [:GPolygon
   [:Boundary
    (map point-element (u/closed-counter-clockwise->open-clockwise (-> poly :Boundary :Points)))]
   [:ExclusiveZone
    (for [b (-> poly :ExclusiveZone :Boundaries)]
      [:Boundary
       (map point-element (u/closed-counter-clockwise->open-clockwise (:Points b)))])]])

(defn- line-element
  "Returns ECHO Line element from given UMM LineType record."
  [line]
  [:Line
   (map point-element (:Points line))])

(defn- convert-horizontal-data-resolutions
  "Converts UMM Spatial Extent values to ECHO10 GeographicCoordinateSystem"
  [spatial-extent]
  (when-let [horizontal-data-resolutions (get-in spatial-extent [:HorizontalSpatialDomain :ResolutionAndCoordinateSystem :HorizontalDataResolution])]
    (let [;; get the first entry in the resolution groups that contains :XDimention or :YDimension.
          horizontal-data-resolution (or (first (:NonGriddedResolutions horizontal-data-resolutions))
                                         (first (:GriddedResolutions horizontal-data-resolutions))
                                         (first (:GenericResolutions horizontal-data-resolutions)))
          x (:XDimension horizontal-data-resolution)
          y (:YDimension horizontal-data-resolution)]
      (when (or x y)
        [:GeographicCoordinateSystem
         [:GeographicCoordinateUnits (:Unit horizontal-data-resolution)]
         [:LatitudeResolution (:YDimension horizontal-data-resolution)]
         [:LongitudeResolution (:XDimension horizontal-data-resolution)]]))))

(defn- convert-geodetic-model
  "Converts UMM Horizontal Coordinate System GeodeticModel to ECHO10 GeodeticModel."
  [spatial-extent]
  (when-let [geodetic-model (get-in spatial-extent [:HorizontalSpatialDomain :ResolutionAndCoordinateSystem :GeodeticModel])]
    (let [datum-name (get geodetic-model :HorizontalDatumName)
          ellipsoid-name (get geodetic-model :EllipsoidName)
          semi-major-axis (get geodetic-model :SemiMajorAxis)
          denominator-of-flattening-ratio (get geodetic-model :DenominatorOfFlatteningRatio)]
      [:GeodeticModel
       [:HorizontalDatumName datum-name]
       [:EllipsoidName ellipsoid-name]
       [:SemiMajorAxis semi-major-axis]
       [:DenominatorOfFlatteningRatio denominator-of-flattening-ratio]])))

(defn- convert-local-coordinate-sys
  "Converts UMM Horizontal Coordinate System GeodeticModel to ECHO10 GeodeticModel."
  [spatial-extent]
  (when-let [local-coordinate-sys (get-in spatial-extent [:HorizontalSpatialDomain :ResolutionAndCoordinateSystem :LocalCoordinateSystem])]
    (let [geo-reference-information (get local-coordinate-sys :GeoReferenceInformation)
          description (get local-coordinate-sys :Description)]
      [:LocalCoordinateSystem
       [:Description description]
       [:GeoReferenceInformation geo-reference-information]])))

(defn spatial-info-element
  "Returns ECHO10 SpatialInfo element from given UMM-C record"
  [c]
  (let [sp (:SpatialExtent c)]
    [:SpatialInfo
     (elements-from sp :SpatialCoverageType)
     [:HorizontalCoordinateSystem
      (convert-geodetic-model sp)
      (convert-horizontal-data-resolutions sp)
      (convert-local-coordinate-sys sp)]]))

(defn- get-orbit-parameters
  "Return the OrbitParameters with the right SwathWidth and Period for ECHO10.
  If SwathWidth exists, convert it to the value in assumed unit. Otherwise,
  Get the largest Footprint in assumed unit, which is Kilometer.
  Lastly, add :Period to the OrbitParameters."
  [collection]
  (when-let [op (get-in collection [:SpatialExtent :OrbitParameters])]
    (let [swath-width (version-collection/get-swath-width collection)
          period (:OrbitPeriod op)]
      (assoc op :SwathWidth swath-width :Period period))))

(defn spatial-element
  "Returns ECHO10 Spatial element from given UMM-C record."
  [c]
  (let [sp (:SpatialExtent c)
        orbit-parameters (get-orbit-parameters c)]
    [:Spatial
     (elements-from sp :SpatialCoverageType)
     (let [horiz (:HorizontalSpatialDomain sp)]
       [:HorizontalSpatialDomain
        (elements-from horiz :ZoneIdentifier)
        (let [geom (:Geometry horiz)]
          [:Geometry
           [:CoordinateSystem (u/coordinate-system geom)]
           (map point-element (:Points geom))
           (map bounding-rect-element (:BoundingRectangles geom))
           (map polygon-element (:GPolygons geom))
           (map line-element (:Lines geom))])])
     (for [vert (spatial-conversion/drop-invalid-vertical-spatial-domains
                 (:VerticalSpatialDomains sp))]
       [:VerticalSpatialDomain
        (elements-from vert :Type :Value)])
     [:OrbitParameters
      (elements-from orbit-parameters
                     :SwathWidth :Period :InclinationAngle
                     :NumberOfOrbits :StartCircularLatitude)]
     [:GranuleSpatialRepresentation (or (:GranuleSpatialRepresentation sp)
                                        u/default-granule-spatial-representation)]]))
