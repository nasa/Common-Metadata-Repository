(ns cmr.umm-spec.migration.spatial-extent-migration
  "Contains helper functions for migrating between different versions of UMM related urls"
  (:require
   [cmr.common.util :refer [update-in-each remove-nils-empty-maps-seqs remove-nil-keys]]
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
      remove-nils-empty-maps-seqs))

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
      remove-nils-empty-maps-seqs))

(defn migrate-up-to-1_15
  "Migrates horizontal data resolutions from 1.14 to 1.15"
  [c]
  (migrate-horizontal-data-resolutions-up-to-1_15 c))

(defn migrate-down-to-1_14
  "Migrates horizontal data resolutions from 1.15 to 1.14"
  [c]
  (migrate-horizontal-data-resolutions-down-to-1_14 c))

(defn migrate-horizontal-data-resolution-single-resolution-down-to-1_15_1
  "Migrates horizontal data point or varies resolution 1.15.2 to 1.15.1"
  [c element]
  (let [element-value (get-in c [:SpatialExtent
                                 :HorizontalSpatialDomain
                                 :ResolutionAndCoordinateSystem
                                 :HorizontalDataResolution
                                 element])]
    (if element-value
      (-> c
        (update-in [:SpatialExtent
                    :HorizontalSpatialDomain
                    :ResolutionAndCoordinateSystem
                    :HorizontalDataResolution] dissoc element)
        (assoc-in [:SpatialExtent
                   :HorizontalSpatialDomain
                   :ResolutionAndCoordinateSystem
                   :HorizontalDataResolution
                   element
                   :HorizontalResolutionProcessingLevelEnum]
                  element-value))
      c)))

;; These are conversion numbers to use when converting nautical miles
;; or statue miles to kilometers. The default significant digits is used in the
;; get-significant-digit-count function.
(def nautical-miles-to-kilometers 1.852001)
(def statute-miles-to-kilometers 1.609344)
(def default-significant-digit 2)

(defn get-significant-digit-count
  "This function counts the significant digits to use when converting statue miles or
   nautical miles to kilometers."
  [number]
  (if (number? number)
    (let [x (clojure.string/split (str number) #"\.")]
      (+ (count (first x)) (count (second x))))
    default-significant-digit))

(defn round2
  "Round a double to the given precision (number of significant digits)"
  [precision d]
  (let [factor (Math/pow 10 precision)]
    (/ (Math/round (* d factor)) factor)))

(defn convert-to-kilometers
  "Convert the value to kilometers using the passed in convertion number."
  [value conversion]
  (when value
    (let [conversion (round2 (get-significant-digit-count value) conversion)]
      (* value conversion))))

(defn convert-all-elements-to-kilometers
  "Convert each element in the resolution to kilometers."
  [resolution conversion]
  (assoc resolution :XDimension (convert-to-kilometers (:XDimension resolution) conversion)
                    :MinimumXDimension (convert-to-kilometers (:MinimumXDimension resolution) conversion)
                    :MaximumXDimension (convert-to-kilometers (:MaximumXDimension resolution) conversion)
                    :YDimension (convert-to-kilometers (:YDimension resolution) conversion)
                    :MinimumYDimension (convert-to-kilometers (:MinimumYDimension resolution) conversion)
                    :MaximumYDimension (convert-to-kilometers (:MaximumYDimension resolution) conversion)
                    :Unit "Kilometers"))

(defn convert-to-kilometers-if-needed
  "This function checks to see if a conversion to kilometers is needed. If it is
   then call a funtion to do the actual conversion."
  [resolution]
  (let [unit (:Unit resolution)
        conversion (case unit
                      "Statute Miles" statute-miles-to-kilometers
                      "Nautical Miles" nautical-miles-to-kilometers
                      nil)]
    (if conversion
      (convert-all-elements-to-kilometers resolution conversion)
      resolution)))

(defn- remove-not-provided-resolutions
  "This function returns nil if the resolution unit has a value of Not provided.
   This essentially removes these resolutions from the end result."
  [resolution]
  (when-not (clojure.string/includes? (:Unit resolution) "Not provided")
    resolution))

(defn migrate-resolution-units-down-to_1_15_1
  "Migrate the resolution down to 1.15.1 by converting any statute miles or
   nautical miles to kilometers and removing any resolutions where the unit is
   Not provided."
  [resolution]
  (-> resolution
      convert-to-kilometers-if-needed
      remove-not-provided-resolutions
      remove-nil-keys))

(defn migrate-resolutions-down-to_1_15_1
  "Migrates a horizontal data resolution group down from 1.15.2 to 1.15.1."
  [c element]
  (let [resolutions (get-in c [:SpatialExtent
                               :HorizontalSpatialDomain
                               :ResolutionAndCoordinateSystem
                               :HorizontalDataResolution
                               element])]
    (if resolutions
      (assoc-in c
                [:SpatialExtent
                 :HorizontalSpatialDomain
                 :ResolutionAndCoordinateSystem
                 :HorizontalDataResolution
                 element]
                (remove-nils-empty-maps-seqs
                  (map #(migrate-resolution-units-down-to_1_15_1 %) resolutions)))
      c)))

(defn migrate-horizontal-data-resolution-units-down-to-1_15_1
  "Migrates horizontal data resolution units from 1.15.2 to 1.15.1"
  [c]
  (-> c
      (migrate-resolutions-down-to_1_15_1 :NonGriddedResolutions)
      (migrate-resolutions-down-to_1_15_1 :NonGriddedRangeResolutions)
      (migrate-resolutions-down-to_1_15_1 :GriddedResolutions)
      (migrate-resolutions-down-to_1_15_1 :GriddedRangeResolutions)
      (migrate-resolutions-down-to_1_15_1 :GenericResolutions)))

(defn migrate-down-to-1_15_1
  "Migrates horizontal data point and veries resolution and the resolution units from 1.15.2
   to 1.15.1"
  [c]
  (-> c
      (migrate-horizontal-data-resolution-single-resolution-down-to-1_15_1 :VariesResolution)
      (migrate-horizontal-data-resolution-single-resolution-down-to-1_15_1 :PointResolution)
      migrate-horizontal-data-resolution-units-down-to-1_15_1))

(defn migrate-horizontal-data-resolution-single-resolution-up-to-1_15_2
  "Migrates horizontal data point or varies resolution from 1.15.1 to 1.15.2. The edn version of a
   collection is passed in and the 1.15.2 collection is passed back out."
  [c element]
  (let [element-value (get-in c [:SpatialExtent
                                 :HorizontalSpatialDomain
                                 :ResolutionAndCoordinateSystem
                                 :HorizontalDataResolution
                                 element
                                 :HorizontalResolutionProcessingLevelEnum])]
    (if element-value
      (-> c
          (update-in [:SpatialExtent
                      :HorizontalSpatialDomain
                      :ResolutionAndCoordinateSystem
                      :HorizontalDataResolution
                      element]
                     dissoc :HorizontalResolutionProcessingLevelEnum)
          (assoc-in [:SpatialExtent
                     :HorizontalSpatialDomain
                     :ResolutionAndCoordinateSystem
                     :HorizontalDataResolution
                     element]
                    element-value))
      c)))

(defn migrate-up-to-1_15_2
  "Migrates horizontal data resolution point and varies from 1.15.1 to 1.15.2. The edn version of a
   collection is passed in and the 1.15.2 collection is passed back out."
  [c]
  (-> c
      (migrate-horizontal-data-resolution-single-resolution-up-to-1_15_2 :VariesResolution)
      (migrate-horizontal-data-resolution-single-resolution-up-to-1_15_2 :PointResolution)))
