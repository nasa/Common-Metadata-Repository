(ns cmr.umm-spec.umm-to-xml-mappings.iso-shared.sensor
  "Functions for generating ISO19115-2 XML elements from UMM sensor records."
  (:require
    [cmr.common.xml.gen :refer :all]
    [cmr.umm-spec.iso19115-2-util :as iso]
    [cmr.umm-spec.umm-to-xml-mappings.iso-shared.characteristics-and-operationalmodes :as ch]
    [cmr.umm-spec.util :as su :refer [with-default char-string]]))

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
  "Returns content generator instructions for the given child instruments."
  [child-instruments]
  (for [child-instrument child-instruments]
    [:gmi:instrument
     [:eos:EOS_Instrument {:id (:sensor-id child-instrument)}
      [:gmi:identifier
       [:gmd:MD_Identifier
        [:gmd:code
         (char-string (:ShortName child-instrument))]
        [:gmd:codeSpace
         (char-string "gov.nasa.esdis.umm.instrumentshortname")]
        [:gmd:description
         (char-string (:LongName child-instrument))]]]
      [:gmi:type
       [:gco:CharacterString (with-default (:Technique child-instrument))]]
      [:gmi:mountedOn {:xlink:href (str "#" (:instrument-id child-instrument))}]
      (ch/generate-characteristics "instrumentInformation" (:Characteristics child-instrument))
      (ch/generate-operationalmodes "instrumentInformation" (:OperationalModes child-instrument))]]))
