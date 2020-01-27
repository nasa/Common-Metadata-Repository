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

(defn get-enum-group
  "Get all the entries in horizontal-data-resolution based on the enum value."
  [horizontal-data-resolutions enum-val]
  (let [group (remove nil? (map #(when (= enum-val (:HorizontalResolutionProcessingLevelEnum %)) %)
                                horizontal-data-resolutions))
        ;; When getting the Generic group, we need to remove the entries with only
        ;; {:HorizontalResolutionProcessingLevelEnum "Not provided"}
        group (remove #(and (= 1 (count (keys %))) (= enum-val umm-spec-util/not-provided)) group)]
    (when (seq group)
      group)))

(defn remove-enum-from-group
  "Remove :HorizontalResolutionProcessingLevelEnum from each element in the group."
  [group]
  (when (seq group)
    (map #(dissoc % :HorizontalResolutionProcessingLevelEnum) group)))

(defn group-resolutions
  "Returns horizontal-data-resolutions in groups."
  [horizontal-data-resolutions]
  (when (seq horizontal-data-resolutions)
    (let [varies (first (get-enum-group horizontal-data-resolutions umm-spec-util/varies))
          point (first (get-enum-group horizontal-data-resolutions umm-spec-util/point))
          non-gridded (get-enum-group horizontal-data-resolutions umm-spec-util/non-gridded)
          non-gridded-range (get-enum-group horizontal-data-resolutions umm-spec-util/non-gridded-range)
          gridded (get-enum-group horizontal-data-resolutions umm-spec-util/gridded)
          generic (get-enum-group horizontal-data-resolutions umm-spec-util/not-provided)
          gridded-range (get-enum-group horizontal-data-resolutions umm-spec-util/gridded-range)]
      (remove-nil-keys
        {:VariesResolution varies
         :PointResolution point
         :NonGriddedResolutions (remove-enum-from-group non-gridded)
         :NonGriddedRangeResolutions (remove-enum-from-group non-gridded-range)
         :GriddedResolutions (remove-enum-from-group gridded)
         :GenericResolutions (remove-enum-from-group generic)
         :GriddedRangeResolutions (remove-enum-from-group gridded-range)}))))

(defn- add-enum-to-group
  "Add :HorizontalResolutionProcessingLevelEnum to each element in group"
  [group enum]
  (when (seq group)
    (map #(if (map? %) (merge {:HorizontalResolutionProcessingLevelEnum enum} %) %) group)))

(defn- add-enum-to-groups
  "Add :HorizontalResolutionProcessingLevelEnum in all resolution groups."
  [group-horizontal-data-resolutions]
  (when (seq group-horizontal-data-resolutions)
    (let [ngr (:NonGriddedResolutions group-horizontal-data-resolutions)
          ngrr (:NonGriddedRangeResolutions group-horizontal-data-resolutions)
          gr (:GriddedResolutions group-horizontal-data-resolutions)
          gnr (:GenericResolutions group-horizontal-data-resolutions)
          grr (:GriddedRangeResolutions group-horizontal-data-resolutions)]
      (remove-nil-keys
        {:VariesResolution (:VariesResolution group-horizontal-data-resolutions)
         :PointResolution (:PointResolution group-horizontal-data-resolutions)
         :NonGriddedResolutions (add-enum-to-group ngr umm-spec-util/non-gridded)
         :NonGriddedRangeResolutions (add-enum-to-group ngrr umm-spec-util/non-gridded-range)
         :GriddedResolutions (add-enum-to-group gr umm-spec-util/gridded)
         :GenericResolutions (add-enum-to-group gnr umm-spec-util/not-provided)
         :GriddedRangeResolutions (add-enum-to-group grr umm-spec-util/gridded-range)}))))

(defn degroup-resolutions
  "Deroup the entries in group-horizontal-data-resolutions."
  [group-horizontal-data-resolutions]
  (when-let [ghdr-with-enum (add-enum-to-groups group-horizontal-data-resolutions)]
    (map remove-nil-keys (flatten (vals ghdr-with-enum)))))

(defn- migrate-horizontal-data-resolutions-up-to-1_15
  "Migrates HorizontalDataResolutions from 1.14 to 1.15.
  Based on the HorizontalResolutionProcessingLevelEnum value, group the entries into resolution groups:
  \"Varies\" -> VariesResolution (Note: If there are multiple entries in the list, only keep one.)
  \"Point\" -> PointResolution (Note: If there are multiple entries in the list, only keep one.)
  \"Not provided\": entry with only this enum won't migrate to anything in 1.15 because it shouldn't exist.
  \"Non Gridded\" -> NonGriddedResolutions
  \"Non Gridded Range\" -> NonGriddedRangeResolutions
  \"Gridded\" -> GriddedResolutions
  \"Not provided\" -> GenericResolutions (When the entry contains more than just the enum.)
  \"Gridded Range\" -> GriddedRangeResolutions."
  [c]
  (let [horizontal-data-resolutions (get-in
                                      c
                                      [:SpatialExtent :HorizontalSpatialDomain
                                       :ResolutionAndCoordinateSystem :HorizontalDataResolutions])
        group-horizontal-data-resolutions (group-resolutions horizontal-data-resolutions)]
    (if (seq group-horizontal-data-resolutions)
      (-> c
          (assoc-in [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem :HorizontalDataResolution]
                    group-horizontal-data-resolutions)
          (update-in [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem]
                     dissoc :HorizontalDataResolutions))
      ;; There is a possiblity in theory that horizontal-data-resolutions only contains
      ;; "Not provided" empty entries, which is not supported in v1.15.
      (update-in
        c
        [:SpatialExtent :HorizontalSpatialDomain
         :ResolutionAndCoordinateSystem]
        dissoc :HorizontalDataResolutions))))

(defn- migrate-horizontal-data-resolutions-down-to-1_14
  "Migrates HorizontalDataResolution from 1.15 to 1.14.
  Add HorizontalResolutionProcessingLevelEnum value to each entry in the resolution group,
  combine all the entries in one list, remove all the resolution groups."
  [c]
  (let [group-horizontal-data-resolutions
         (get-in c
                 [:SpatialExtent :HorizontalSpatialDomain
                  :ResolutionAndCoordinateSystem :HorizontalDataResolution])
        degroup-horizontal-data-resolutions (degroup-resolutions group-horizontal-data-resolutions)]
    (if (seq degroup-horizontal-data-resolutions)
      (-> c
          (assoc-in [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem :HorizontalDataResolutions]
                    degroup-horizontal-data-resolutions)
          (update-in [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem]
                     dissoc :HorizontalDataResolution))
      c)))

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

(defn migrate-up-to-1_15
  "Migrates horizontal data resolutions from 1.14 to 1.15"
  [c]
  (migrate-horizontal-data-resolutions-up-to-1_15 c))

(defn migrate-down-to-1_14
  "Migrates horizontal data resolutions from 1.15 to 1.14"
  [c]
  (migrate-horizontal-data-resolutions-down-to-1_14 c))
