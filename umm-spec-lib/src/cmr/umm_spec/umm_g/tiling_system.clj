(ns cmr.umm-spec.umm-g.tiling-system
  "Contains functions for parsing UMM-G JSON TilingIdentificationSystem into umm-lib granule model
   TwoDCoordinateSystem and generating UMM-G JSON TilingIdentificationSystem
   from umm-lib granule model TwoDCoordinateSystem."
  (:require
   [cmr.umm.umm-granule :as g])
  (:import cmr.umm.umm_granule.UmmGranule))

(defn umm-g-tiling-identification-system->TwoDCoordinateSystem
  "Returns the umm-lib granule model TwoDCoordinateSystem from the given UMM-G TilingIdentificationSystem."
  [tiling-identification-system]
  (when tiling-identification-system
    (let [{:keys [TilingIdentificationSystemName Coordinate1 Coordinate2]}
          tiling-identification-system]
      (g/map->TwoDCoordinateSystem
        {:name TilingIdentificationSystemName
         :start-coordinate-1 (:MinimumValue Coordinate1)
         :end-coordinate-1 (:MaximumValue Coordinate1)
         :start-coordinate-2 (:MinimumValue Coordinate2)
         :end-coordinate-2 (:MaximumValue Coordinate2)}))))

(defn TwoDCoordinateSystem->umm-g-tiling-identification-system
  "Returns the UMM-G TilingIdentificationSystem from the given umm-lib granule model TwoDCoordinateSystem."
  [two-d-coordinate-system]
  (when two-d-coordinate-system
    (let [{:keys [name start-coordinate-1 end-coordinate-1 start-coordinate-2 end-coordinate-2]}
          two-d-coordinate-system]
      ;; TilingIdentificationSystemName is an enumerated value, we will need to fix this to prevent
      ;; schema validation issues.
      {:TilingIdentificationSystemName name
       :Coordinate1 {:MinimumValue start-coordinate-1
                     :MaximumValue end-coordinate-1}
       :Coordinate2 {:MinimumValue start-coordinate-2
                     :MaximumValue end-coordinate-2}})))
