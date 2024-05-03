(ns cmr.umm-spec.umm-g.characteristic
  "Contains functions for parsing UMM-G JSON characteristics into umm-lib granule model
  CharacteristicRefs and generating UMM-G JSON characteristics from umm-lib granule model
  CharacteristicRefs."
  (:require
   [cmr.umm.umm-granule :as g])
  (:import cmr.umm.umm_granule.UmmGranule))

(defn- umm-g-characteristic->CharacteristicRef
  "Returns the umm-lib granule model CharacteristicRef from the given UMM-G Characteristic."
  [characteristic]
  (g/map->CharacteristicRef
   {:name (:Name characteristic)
    :value (:Value characteristic)}))

(defn umm-g-characteristics->CharacteristicRefs
  "Returns the umm-lib granule model CharacteristicRefs from the given UMM-G Characteristics."
  [characteristics]
  (seq (map umm-g-characteristic->CharacteristicRef characteristics)))

(defn CharacteristicRefs->umm-g-characteristics
  "Returns the UMM-G Characteristics from the given umm-lib granule model CharacteristicRefs."
  [characteristic-refs]
  (when (seq characteristic-refs)
    (for [characteristic-ref characteristic-refs]
      {:Name (:name characteristic-ref)
       :Value (:value characteristic-ref)})))
