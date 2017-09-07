(ns cmr.umm-spec.migration.distance-units-migration
  "Contains helper functions for migrating between different versions of UMM vertical coordinate system's
   distance units"
  (:require
   [clojure.string :as string]
   [cmr.common.util :as util]))

(def altitude-distance-units-mapping
  "Defines mappings of altitude distance units from v1.9 to v1.10."
  {"HECTOPASCALS" "HectoPascals"
   "KILOMETERS" "Kilometers"
   "MILLIBARS" "Millibars"})

(def depth-distance-units-mapping
  "Defines mappings of depth distance units from v1.9 to v1.10."
  {"FATHOMS" "Fathoms"
   "FEET" "Feet"
   "HECTOPASCALS" "HectoPascals"
   "METERS" "Meters"
   "MILLIBARS" "Millibars"})

(defn migrate-distance-units-to-enum
  "Migrate distance units from string to enum."
  [c]
  (let [old-altitude-distance-units (-> c
                                        :SpatialInformation
                                        :VerticalCoordinateSystem
                                        :AltitudeSystemDefinition
                                        :DistanceUnits)
        altitude-distance-units (get altitude-distance-units-mapping
                                     (when old-altitude-distance-units
                                       (string/upper-case old-altitude-distance-units)))
        old-depth-distance-units (-> c
                                     :SpatialInformation
                                     :VerticalCoordinateSystem
                                     :DepthSystemDefinition
                                     :DistanceUnits)
        depth-distance-units (get depth-distance-units-mapping
                                  (when old-depth-distance-units
                                    (string/upper-case old-depth-distance-units)))]
      (-> c
          (assoc-in [:SpatialInformation :VerticalCoordinateSystem :AltitudeSystemDefinition :DistanceUnits]  
                    altitude-distance-units)
          (assoc-in [:SpatialInformation :VerticalCoordinateSystem :DepthSystemDefinition :DistanceUnits]
                    depth-distance-units)
          ;; :SpatialInformation could become an empty map after setting DistanceUnits to nil
          util/remove-empty-maps)))
