(ns cmr.umm.dif10.collection.instrument
  "Functions to parse and generate DIF10 Instrument elements which are part of Platform elements"
  (:require
   [clojure.data.xml :as xml]
   [cmr.common.xml :as cx]
   [cmr.umm.umm-collection :as coll]
   [cmr.umm.dif10.collection.sensor :as sensor]
   [cmr.umm.dif10.collection.characteristic :as ch]))

(defn xml-elem->Instrument
  [instrument-elem]
  (coll/map->Instrument
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
      (xml/element :OperationalMode {} operation-mode))))

(defn generate-instruments
  [instruments]
  (if (seq instruments)
    (for [{:keys [long-name short-name technique
                  sensors characteristics operation-modes]} instruments]
      (xml/element :Instrument {}
                 (xml/element :Short_Name {} short-name)
                 (when long-name (xml/element :Long_Name {} long-name))
                 (when technique (xml/element :Technique {} technique))
                 (ch/generate-characteristics characteristics)
                 (generate-operation-modes operation-modes)
                 (sensor/generate-sensors sensors)))
    ;; Added since Instrument is a required field in DIF10. CMRIN-77
    (xml/element :Instrument {}
               (xml/element :Short_Name {} coll/not-provided))))
