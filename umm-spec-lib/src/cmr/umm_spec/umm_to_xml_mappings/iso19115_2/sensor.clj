(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2.sensor
  "Functions for generating ISO19115-2 XML elements from UMM sensor records."
  (:require [cmr.umm-spec.xml.gen :refer :all]
            [cmr.umm-spec.iso19115-2-util :as iso]
            [cmr.umm-spec.util :as su :refer [with-default]]
            [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.characteristics :as ch]))

(defn- sensor-with-id
  "Returns the sensor with generated ids for ISO xml generation"
  [instrument-id sensor]
  (let [sensor-id (su/generate-id)]
    (-> sensor
        (assoc :sensor-id sensor-id)
        (assoc :instrument-id instrument-id))))

(defn sensors-with-id
  "Returns the sensors with generated ids for ISO xml generation"
  [sensors instrument-id]
  (map (partial sensor-with-id instrument-id) sensors))

(defn generate-sensors
  "Returns content generator instructions for the given sensors."
  [sensors]
  (for [sensor sensors]
    [:eos:sensor
     [:eos:EOS_Sensor
      [:eos:citation
       [:gmd:CI_Citation
        [:gmd:title
         [:gco:CharacterString (iso/generate-title sensor)]]
        [:gmd:date {:gco:nilReason "unknown"}]]]
      [:eos:identifier
       [:gmd:MD_Identifier
        [:gmd:code
         (char-string (:ShortName sensor))]
        [:gmd:description
         (char-string (:LongName sensor))]]]
      [:eos:type
       (char-string (with-default (:Technique sensor)))]
      [:eos:description {:gco:nilReason "missing"}]
      [:eos:mountedOn {:xlink:href (str "#" (:instrument-id sensor))}]
      iso/eos-echo-attributes-info
      (ch/generate-characteristics "sensorInformation" (:Characteristics sensor))]]))

