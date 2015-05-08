(ns cmr.umm.dif10.collection.instrument
  "Functions to parse and generate DIF10 Instrument elements which are part of Platform elements"
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]
            [cmr.umm.dif10.collection.sensor :as sensor]
            [cmr.umm.dif10.collection.characteristic :as ch]))

(defn xml-elem->Instrument
  [instrument-elem]
  (c/map->Instrument
    {:short-name (cx/string-at-path instrument-elem [:Short_Name])
     :long-name (cx/string-at-path instrument-elem [:Long_Name])
     :technique (cx/string-at-path instrument-elem [:Technique])
     :sensors (sensor/xml-elem->Sensors instrument-elem)
     :characteristics (ch/xml-elem->Characteristics instrument-elem)
     :operation-modes (seq (cx/strings-at-path instrument-elem [:OperationalMode]))}))

(defn xml-elem->Instruments
  [platform-element]
  (seq (map xml-elem->Instrument
            (cx/elements-at-path
              platform-element
              [:Instrument]))))

(defn generate-operation-modes
  [operation-modes]
  (when (seq operation-modes)
    (for [operation-mode operation-modes]
      (x/element :OperationalMode {} operation-mode))))

(defn generate-instruments
  [instruments]
  (if (seq instruments)
    (for [instrument instruments]
      (let [{:keys [long-name short-name technique sensors characteristics operation-modes]} instrument]
        (x/element :Instrument {}
                   (x/element :Short_Name {} short-name)
                   (when long-name (x/element :Long_Name {} long-name))
                   (when technique (x/element :Technique {} technique))
                   (ch/generate-characteristics characteristics)
                   (generate-operation-modes operation-modes)
                   (sensor/generate-sensors sensors))))
    ;;Added since Instrument is a required field in DIF10
    (x/element :Instrument {}
               (x/element :Short_Name {} "Not provided"))))