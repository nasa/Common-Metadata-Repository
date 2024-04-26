(ns cmr.umm-spec.umm-g.instrument
  "Contains functions for parsing UMM-G JSON instruments into umm-lib granule model InstrumentRefs
  and generating UMM-G JSON instruments from umm-lib granule model InstrumentRefs."
  (:require
   [cmr.umm-spec.umm-g.characteristic :as characteristic]
   [cmr.umm-spec.umm-g.sensor :as sensor]
   [cmr.umm.umm-granule :as g])
  (:import cmr.umm.umm_granule.UmmGranule))

(defn- umm-g-instrument->InstrumentRef
  "Returns the umm-lib granule model InstrumentRef from the given UMM-G Instrument."
  [instrument]
  (let [short-name (:ShortName instrument)
        characteristic-refs (characteristic/umm-g-characteristics->CharacteristicRefs
                             (:Characteristics instrument))
        sensor-refs (sensor/umm-g-sensors->SensorRefs (:ComposedOf instrument))
        operation-modes (:OperationalModes instrument)]
    (g/map->InstrumentRef
     {:short-name short-name
      :characteristic-refs characteristic-refs
      :sensor-refs sensor-refs
      :operation-modes operation-modes})))

(defn umm-g-instruments->InstrumentRefs
  "Returns the umm-lib granule model InstrumentRefs from the given UMM-G Instruments."
  [instruments]
  (seq (map umm-g-instrument->InstrumentRef instruments)))

(defn InstrumentRefs->umm-g-instruments
  "Returns the UMM-G Instruments from the given umm-lib granule model InstrumentRefs."
  [instrument-refs]
  (when (seq instrument-refs)
    (for [instrument-ref instrument-refs]
      (let [{:keys [short-name characteristic-refs sensor-refs operation-modes]} instrument-ref]
        {:ShortName short-name
         :Characteristics (characteristic/CharacteristicRefs->umm-g-characteristics
                           characteristic-refs)
         :ComposedOf (sensor/SensorRefs->umm-g-sensors sensor-refs)
         :OperationalModes operation-modes}))))
