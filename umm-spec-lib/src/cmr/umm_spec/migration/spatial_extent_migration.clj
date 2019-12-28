(ns cmr.umm-spec.migration.spatial-extent-migration
  "Contains helper functions for migrating between different versions of UMM related urls"
  (:require
   [cmr.common.util :refer [update-in-each remove-empty-maps remove-nil-keys]]
   [cmr.umm-spec.util :as umm-spec-util]))

(defn dissoc-center-point
  "Remove the :CenterPoint element from the path"
  [m path]
  (if (not= nil (get-in m path))
    (update-in-each m path #(dissoc % :CenterPoint))
    m))

(defn remove-center-point
  "Remove :CenterPoint from :BoundingRectangles, :GPolyons and :Lines
  to comply with UMM spec v1.9"
  [spatial-extent]
  (-> spatial-extent
    (dissoc-center-point [:HorizontalSpatialDomain :Geometry :BoundingRectangles])
    (dissoc-center-point [:HorizontalSpatialDomain :Geometry :GPolygons])
    (dissoc-center-point [:HorizontalSpatialDomain :Geometry :Lines])))

(defn- migrate-geographic-coordinate-system
  "If GeographicCoordinateSystem exists, migrates it to version 1.14 values."
  [c]
  (let [geographic-coordinate-system (get-in c [:SpatialInformation :HorizontalCoordinateSystem
                                                :GeographicCoordinateSystem])
        longitude-resolution (when geographic-coordinate-system
                               (get geographic-coordinate-system :LongitudeResolution))
        latitude-resolution (when geographic-coordinate-system
                              (get geographic-coordinate-system :LatitudeResolution))
        geographic-coordinate-unit (when geographic-coordinate-system
                                     (get geographic-coordinate-system :GeographicCoordinateUnits))]
    (if geographic-coordinate-system
      (-> c
          (assoc-in [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem
                     :HorizontalDataResolutions]
                    [{:HorizontalResolutionProcessingLevelEnum umm-spec-util/not-provided
                      :XDimension longitude-resolution
                      :YDimension latitude-resolution
                      :Unit geographic-coordinate-unit}])
          (update-in [:SpatialInformation :HorizontalCoordinateSystem] dissoc :GeographicCoordinateSystem))
      c)))

(defn- migrate-geodetic-model-up
  "If geodetic model exists, migrates it to 1.14 values"
  [c]
  (if-let [geodetic-model (get-in c [:SpatialInformation :HorizontalCoordinateSystem :GeodeticModel])]
    (-> c
        (assoc-in [:SpatialExtent :HorizontalSpatialDomain
                   :ResolutionAndCoordinateSystem :GeodeticModel]
                  geodetic-model)
        (update-in [:SpatialInformation :HorizontalCoordinateSystem]
                   dissoc :GeodeticModel))
    c))

(defn- migrate-geodetic-model-down
  "If geodetic model exists, migrates it to 1.13 values"
  [c]
  (if-let [geodetic-model (get-in c [:SpatialExtent :HorizontalSpatialDomain
                                     :ResolutionAndCoordinateSystem :GeodeticModel])]
    (-> c
        (assoc-in [:SpatialInformation :HorizontalCoordinateSystem :GeodeticModel]
                  geodetic-model)
        (update-in [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem]
                   dissoc :GeodeticModel))
    c))

(defn- migrate-local-coordinate-system-up
  "If local coordinate system exists, migrates it to 1.14 values"
  [c]
  (if-let [local-coordinate-system (get-in c [:SpatialInformation :HorizontalCoordinateSystem
                                              :LocalCoordinateSystem])]
    (-> c
        (assoc-in [:SpatialExtent :HorizontalSpatialDomain
                   :ResolutionAndCoordinateSystem :LocalCoordinateSystem]
                  local-coordinate-system)
        (update-in [:SpatialInformation :HorizontalCoordinateSystem] dissoc :LocalCoordinateSystem))
    c))

(defn- migrate-local-coordinate-system-down
  "If local coordinate system exists, migrates it to 1.13 values"
  [c]
  (if-let [local-coordinate-system (get-in c [:SpatialExtent :HorizontalSpatialDomain
                                              :ResolutionAndCoordinateSystem :LocalCoordinateSystem])]
    (-> c
        (assoc-in [:SpatialInformation :HorizontalCoordinateSystem :LocalCoordinateSystem]
                  local-coordinate-system)
        (update-in [:SpatialExtent :HorizontalSpatialDomain
                    :ResolutionAndCoordinateSystem]
                   dissoc :LocalCoordinateSystem))
    c))

(defn- migrate-horizontal-data-resolutions
  "Migrates HorizontalDataResolutions from 1.14, using the first found, to the values in 1.13."
  [c]
  (if-let [horizontal-data-resolution (first (get-in
                                              c
                                              [:SpatialExtent :HorizontalSpatialDomain
                                               :ResolutionAndCoordinateSystem :HorizontalDataResolutions]))]
    (-> c
        (assoc-in [:SpatialInformation :HorizontalCoordinateSystem
                   :GeographicCoordinateSystem]
                  {:GeographicCoordinateUnits (:Unit horizontal-data-resolution)
                   :LongitudeResolution (:XDimension horizontal-data-resolution)
                   :LatitudeResolution (:YDimension horizontal-data-resolution)})
        (update-in [:SpatialExtent :HorizontalSpatialDomain
                    :ResolutionAndCoordinateSystem]
                   dissoc :HorizontalDataResolutions))
    c))

(defn migrate-up-to-1_14
  "Migrates Distributions from 1.13 to 1.14"
  [c]
  (-> c
      migrate-geographic-coordinate-system
      migrate-geodetic-model-up
      migrate-local-coordinate-system-up
      remove-nil-keys
      remove-empty-maps))

(defn- migrate-spatial-coverage-type
  "Migrate the SpatialCoverageType value in 1.14 to the value in 1.13."
  [spatial-coverage-type]
  (if (or (= "HORIZONTAL_ORBITAL" spatial-coverage-type)
          (= "HORIZONTAL_VERTICAL_ORBITAL" spatial-coverage-type))
    "ORBITAL"
    spatial-coverage-type))

(defn migrate-down-to-1_13
  "Migrates Distributions from 1.14 to 1.13"
  [c]
  (-> c
      migrate-geodetic-model-down
      migrate-horizontal-data-resolutions
      migrate-local-coordinate-system-down
      (update-in [:SpatialExtent :HorizontalSpatialDomain] dissoc :ResolutionAndCoordinateSystem)
      (update-in [:SpatialExtent :SpatialCoverageType] migrate-spatial-coverage-type)
      remove-nil-keys
      remove-empty-maps))
