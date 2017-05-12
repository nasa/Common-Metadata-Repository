(ns cmr.umm-spec.umm-to-xml-mappings.iso-shared.instrument
  "Functions for generating ISOSMAP XML elements from UMM instrument records."
  (:require
    [cmr.common.xml.gen :refer :all]
    [cmr.umm-spec.iso19115-2-util :as iso]
    [cmr.umm-spec.iso-keywords :as kws]
    [cmr.umm-spec.umm-to-xml-mappings.iso-shared.characteristics-and-operationalmodes :as ch]
    [cmr.umm-spec.umm-to-xml-mappings.iso-shared.sensor :as sensor]
    [cmr.umm-spec.util :as su :refer [with-default char-string]]))

(defn- instrument-with-id
  "Returns the instrument with generated ids for ISO xml generation"
  [platform-id instrument]
  (let [instrument-id (su/generate-id)
        sensors (sensor/sensors-with-id (:ComposedOf instrument) instrument-id)]
    (-> instrument
        (assoc :ComposedOf sensors)
        (assoc :instrument-id instrument-id)
        (assoc :platform-id platform-id))))

(defn instruments-with-id
  "Returns the instruments with generated ids for ISO xml generation"
  [instruments platform-id]
  (map (partial instrument-with-id platform-id) instruments))

(defn generate-instrument-keywords
  [instruments]
  (let [keywords (map iso/generate-title instruments)]
    (kws/generate-iso-smap-descriptive-keywords "instrument" keywords)))

(defn generate-instruments
  "Returns content generator instructions for the given instruments."
  [instruments]
  (for [instrument instruments]
    [:gmi:instrument
     [:eos:EOS_Instrument {:id (:instrument-id instrument)}
      [:gmi:identifier
       [:gmd:MD_Identifier
        [:gmd:code
         (char-string (:ShortName instrument))]
        [:gmd:codeSpace
         (char-string "gov.nasa.esdis.umm.instrumentshortname")]
        [:gmd:description
         (char-string (:LongName instrument))]]]
      [:gmi:type
       [:gco:CharacterString (with-default (:Technique instrument))]]
      [:gmi:mountedOn {:xlink:href (str "#" (:platform-id instrument))}]
      (ch/generate-characteristics "instrumentInformation" (:Characteristics instrument))
      (ch/generate-operationalmodes "instrumentInformation" (:OperationalModes instrument))]]))

(defn generate-child-instruments
  "Returns content generator instructions for the given instruments."
  [instruments]
  (for [instrument instruments]
      (sensor/generate-sensors (:ComposedOf instrument))))
