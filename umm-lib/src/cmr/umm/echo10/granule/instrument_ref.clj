(ns cmr.umm.echo10.granule.instrument-ref
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-granule :as g]
            [cmr.umm.echo10.granule.characteristic-ref :as cref]
            [cmr.umm.echo10.granule.sensor-ref :as sensor]
            [cmr.umm.echo10.collection.instrument :as ins]))

(defn xml-elem->InstrumentRef
  [instrument-ref-elem]
  (let [short-name (cx/string-at-path instrument-ref-elem [:ShortName])
        characteristic-refs (cref/xml-elem->CharacteristicRefs instrument-ref-elem)
        sensor-refs (sensor/xml-elem->SensorRefs instrument-ref-elem)
        operation-modes (seq (cx/strings-at-path instrument-ref-elem [:OperationModes :OperationMode]))]
    (g/map->InstrumentRef {:short-name short-name
                           :characteristic-refs characteristic-refs
                           :sensor-refs sensor-refs
                           :operation-modes operation-modes})))

(defn xml-elem->InstrumentRefs
  [platform-element]
  (seq (map xml-elem->InstrumentRef
            (cx/elements-at-path
              platform-element
              [:Instruments :Instrument]))))

(defn generate-instrument-refs
  [instrument-refs]
  (when (seq instrument-refs)
    (x/element
      :Instruments {}
      (for [{:keys [short-name characteristic-refs sensor-refs operation-modes]} instrument-refs]
          (x/element :Instrument {}
                     (x/element :ShortName {} short-name)
                     (cref/generate-characteristic-refs characteristic-refs)
                     (sensor/generate-sensor-refs sensor-refs)
                     (ins/generate-operation-modes operation-modes))))))
