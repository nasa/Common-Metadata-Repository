(ns cmr.umm.echo10.collection.sensor
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.echo10.collection.characteristic :as ch]))

(defn xml-elem->Sensor
  [sensor-elem]
  (let [short-name (cx/string-at-path sensor-elem [:ShortName])
        long-name (cx/string-at-path sensor-elem [:LongName])
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
              [:Sensors :Sensor]))))

(defn generate-sensors
  [sensors]
  (when-not (empty? sensors)
    (x/element
      :Sensors {}
      (for [sensor sensors]
        (let [{:keys [short-name long-name technique characteristics]} sensor]
          (x/element :Sensor {}
                     (x/element :ShortName {} short-name)
                     (when long-name (x/element :LongName {} long-name))
                     (when technique (x/element :Technique {} technique))
                     (ch/generate-characteristics characteristics)))))))
