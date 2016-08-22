(ns cmr.umm.echo10.granule.sensor-ref
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-granule :as g]
            [cmr.umm.echo10.granule.characteristic-ref :as cref]))

(defn xml-elem->SensorRef
  [sensor-ref-elem]
  (let [short-name (cx/string-at-path sensor-ref-elem [:ShortName])
        characteristic-refs (cref/xml-elem->CharacteristicRefs sensor-ref-elem)]
    (g/map->SensorRef {:short-name short-name
                       :characteristic-refs characteristic-refs})))

(defn xml-elem->SensorRefs
  [instrument-element]
  (seq (map xml-elem->SensorRef
            (cx/elements-at-path
              instrument-element
              [:Sensors :Sensor]))))

(defn generate-sensor-refs
  [sensor-refs]
  (when (seq sensor-refs)
    (x/element
      :Sensors {}
      (for [{:keys [short-name characteristic-refs]} sensor-refs]
        (x/element :Sensor {}
                   (x/element :ShortName {} short-name)
                   (cref/generate-characteristic-refs characteristic-refs))))))
