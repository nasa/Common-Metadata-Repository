(ns cmr.umm-spec.migration.distance-units-migration
  "Contains helper functions for migrating between different versions of UMM vertical coordinate system's
   distance units"
  (:require
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
  (let [old-altitude-distance-units (get-in c [:SpatialInformation
                                               :VerticalCoordinateSystem
                                               :AltitudeSystemDefinition
                                               :DistanceUnits])
        altitude-distance-units (get altitude-distance-units-mapping
                                     (util/safe-uppercase old-altitude-distance-units))
        old-depth-distance-units (get-in c [:SpatialInformation
                                            :VerticalCoordinateSystem
                                            :DepthSystemDefinition
                                            :DistanceUnits])
        depth-distance-units (get depth-distance-units-mapping
                                  (util/safe-uppercase old-depth-distance-units))]
      (-> c
          (assoc-in [:SpatialInformation :VerticalCoordinateSystem :AltitudeSystemDefinition :DistanceUnits]  
                    altitude-distance-units)
          (assoc-in [:SpatialInformation :VerticalCoordinateSystem :DepthSystemDefinition :DistanceUnits]
                    depth-distance-units))))
