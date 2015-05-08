(ns cmr.umm.dif10.collection.sensor
  "Functions to parse and generate DIF10 sensor elements which are part of instrument elements"
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]
            [cmr.umm.dif10.collection.characteristic :as ch]))

(defn xml-elem->Sensor
  [sensor-elem]
  (let [short-name (cx/string-at-path sensor-elem [:Short_Name])
        long-name (cx/string-at-path sensor-elem [:Long_Name])
        technique (cx/string-at-path sensor-elem [:Technique])
        characteristics (ch/xml-elem->Characteristics sensor-elem)]
    (c/map->Sensor {:short-name short-name
                    :long-name long-name
                    :technique technique
                    :characteristics characteristics})))

(defn xml-elem->Sensors
  [instrument-element]
  (seq (map xml-elem->Sensor
            (cx/elements-at-path
              instrument-element
              [:Sensor]))))

(defn generate-sensors
  [sensors]
  (when-not (empty? sensors)
    (for [sensor sensors]
      (let [{:keys [short-name long-name technique characteristics]} sensor]
        (x/element :Sensor {}
                   (x/element :Short_Name {} short-name)
                   (when long-name (x/element :Long_Name {} long-name))
                   (when technique (x/element :Technique {} technique))
                   (ch/generate-characteristics characteristics))))))