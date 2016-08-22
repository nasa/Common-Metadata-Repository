(ns cmr.umm.echo10.collection.instrument
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.echo10.collection.sensor :as sensor]
            [cmr.umm.echo10.collection.characteristic :as ch]))

(defn xml-elem->Instrument
  [instrument-elem]
  (let [short-name (cx/string-at-path instrument-elem [:ShortName])
        long-name (cx/string-at-path instrument-elem [:LongName])
        technique (cx/string-at-path instrument-elem [:Technique])
        sensors (sensor/xml-elem->Sensors instrument-elem)
        characteristics (ch/xml-elem->Characteristics instrument-elem)
        operation-modes (seq (cx/strings-at-path instrument-elem [:OperationModes :OperationMode]))]
    (c/map->Instrument {:short-name short-name
                        :long-name long-name
                        :technique technique
                        :sensors sensors
                        :characteristics characteristics
                        :operation-modes operation-modes})))

(defn xml-elem->Instruments
  [platform-element]
  (seq (map xml-elem->Instrument
            (cx/elements-at-path
              platform-element
              [:Instruments :Instrument]))))

(defn generate-operation-modes
  [operation-modes]
  (when (seq operation-modes)
    (x/element :OperationModes {}
               (for [operation-mode operation-modes]
                 (x/element :OperationMode {} operation-mode)))))

(defn generate-instruments
  [instruments]
  (when (seq instruments)
    (x/element
      :Instruments {}
      (for [instrument instruments]
        (let [{:keys [long-name short-name technique sensors characteristics operation-modes]} instrument]
          (x/element :Instrument {}
                     (x/element :ShortName {} short-name)
                     (when long-name (x/element :LongName {} long-name))
                     (when technique (x/element :Technique {} technique))
                     (ch/generate-characteristics characteristics)
                     (sensor/generate-sensors sensors)
                     (generate-operation-modes operation-modes)))))))
