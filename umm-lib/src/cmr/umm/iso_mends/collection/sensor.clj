(ns cmr.umm.iso-mends.collection.sensor
  "Contains functions for parsing and generating the ISO MENDS sensor"
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.iso-mends.collection.helper :as h]
            [cmr.umm.generator-util :as gu]))

(defn xml-elem->Sensor
  [sensor-elem]
  (let [short-name (cx/string-at-path sensor-elem
                                      [:identifier :MD_Identifier :code :CharacterString])
        long-name (cx/string-at-path sensor-elem
                                     [:identifier :MD_Identifier :description :CharacterString])]
    (c/map->Sensor {:short-name short-name
                    :long-name long-name})))

(defn xml-elem->Sensors
  [instrument-element]
  (seq (map xml-elem->Sensor
            (cx/elements-at-path
              instrument-element
              [:sensor :EOS_Sensor]))))

(defn- sensor-with-id
  "Returns the sensor with generated ids for ISO xml generation"
  [instrument-id sensor]
  (let [sensor-id (gu/generate-id)]
    (-> sensor
        (assoc :sensor-id sensor-id)
        (assoc :instrument-id instrument-id))))

(defn sensors-with-id
  "Returns the sensors with generated ids for ISO xml generation"
  [sensors instrument-id]
  (map (partial sensor-with-id instrument-id) sensors))

(defn generate-sensors
  [sensors]
  (for [sensor sensors]
    (let [{:keys [short-name long-name sensor-id instrument-id]} sensor
          title (if (empty? long-name) short-name (str short-name " > " long-name))]
      (x/element :eos:sensor {}
                 (x/element :eos:EOS_Sensor {:id sensor-id}
                            (x/element :eos:citation {}
                                       (x/element :gmd:CI_Citation {}
                                                  (h/iso-string-element :gmd:title title)
                                                  (x/element :gmd:date {:gco:nilReason "unknown"})))
                            (x/element :eos:identifier {}
                                       (x/element :gmd:MD_Identifier {}
                                                  (h/iso-string-element :gmd:code short-name)
                                                  (h/iso-string-element :gmd:description long-name)))
                            (x/element :eos:type {:gco:nilReason "missing"})
                            (x/element :eos:mountedOn {:xlink:href (str "#" instrument-id)}))))))
