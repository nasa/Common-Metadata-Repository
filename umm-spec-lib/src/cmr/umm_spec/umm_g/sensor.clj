(ns cmr.umm-spec.umm-g.sensor
  "Contains functions for parsing UMM-G JSON sensors into umm-lib granule model ComposedOfs
  and generating UMM-G JSON sensors from umm-lib granule model ComposedOfs."
  (:require
   [cmr.umm-spec.umm-g.characteristic :as characteristic]
   [cmr.umm.umm-granule :as g])
  (:import cmr.umm.umm_granule.UmmGranule))

(defn- umm-g-sensor->SensorRef
  "Returns the umm-lib granule model SensorRef from the given UMM-G ComposedOf."
  [sensor]
  (let [short-name (:ShortName sensor)
        characteristic-refs (characteristic/umm-g-characteristics->CharacteristicRefs
                             (:Characteristics sensor))]
    (g/map->SensorRef
     {:short-name short-name
      :characteristic-refs characteristic-refs})))

(defn umm-g-sensors->SensorRefs
  "Returns the umm-lib granule model SensorRefs from the given UMM-G ComposedOfs."
  [sensors]
  (seq (map umm-g-sensor->SensorRef sensors)))

(defn SensorRefs->umm-g-sensors
  "Returns the UMM-G ComposedOfs from the given umm-lib granule model SensorRefs."
  [sensor-refs]
  (when (seq sensor-refs)
    (for [sensor-ref sensor-refs]
      (let [{:keys [short-name characteristic-refs]} sensor-ref]
        {:ShortName short-name
         :Characteristics (characteristic/CharacteristicRefs->umm-g-characteristics
                           characteristic-refs)}))))
