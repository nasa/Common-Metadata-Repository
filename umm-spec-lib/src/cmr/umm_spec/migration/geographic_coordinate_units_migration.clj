(ns cmr.umm-spec.migration.geographic-coordinate-units-migration
  "Contains helper functions for migrating between different versions of UMM Horizontal coordinate system's
   distance units"
  (:require
   [cmr.common.util :as util]))

(def geographic-coordinate-units-mapping
  "Defines mappings of geographic coordinate units from v1.9 to v1.10."
  {"DECIMAL DEGREES" "Decimal Degrees"
   "KILOMETERS" "Kilometers"
   "METERS" "Meters"})

(defn migrate-geographic-coordinate-units-to-enum
  "Migrate geographic coordinate units from string to enum."
  [c]
  (let [old-geographic-coordinate-units (get-in c [:SpatialInformation
                                                   :HorizontalCoordinateSystem
                                                   :GeographicCoordinateSystem
                                                   :GeographicCoordinateUnits])
        geographic-coordinate-units (get geographic-coordinate-units-mapping
                                      (util/safe-uppercase old-geographic-coordinate-units))]
      (-> c
          (assoc-in [:SpatialInformation :HorizontalCoordinateSystem 
                     :GeographicCoordinateSystem :GeographicCoordinateUnits]  
                    geographic-coordinate-units)))) 
