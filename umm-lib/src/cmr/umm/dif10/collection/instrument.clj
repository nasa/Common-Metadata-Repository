(ns cmr.umm.dif10.collection.instrument
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]
            [cmr.umm.dif10.collection.sensor :as sensor]
            [cmr.umm.dif10.collection.characteristic :as ch]))

(defn xml-elem->Instrument
  [instrument-elem]
  (let [short-name (cx/string-at-path instrument-elem [:Short_Name])
        long-name (cx/string-at-path instrument-elem [:Long_Name])
        technique (cx/string-at-path instrument-elem [:Technique])
        characteristics (ch/xml-elem->Characteristics instrument-elem)
        operation-modes (seq (cx/strings-at-path instrument-elem [:OperationalMode]))
        sensors (sensor/xml-elem->Sensors instrument-elem)]
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
              [:Instrument]))))