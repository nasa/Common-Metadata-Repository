(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2.instrument
  "Functions for generating ISO19115-2 XML elements from UMM instrument records."
  (:require [cmr.common.xml.gen :refer :all]
            [cmr.umm-spec.iso19115-2-util :as iso]
            [cmr.umm-spec.iso-keywords :as kws]
            [cmr.umm-spec.util :as su :refer [with-default char-string]]
            [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.sensor :as sensor]
            [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.characteristics :as ch]))

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

(defn- get-instrument-tag
  "Returns the instrument tag :eos:EOS_Instrument if there are sensors in the instrument,
  otherwise returns :gmi:MI_Instrument"
  [instrument]
  (if (or (seq (:ComposedOf instrument))
          (seq (:Characteristics instrument)))
    :eos:EOS_Instrument
    :gmi:MI_Instrument))

(defn generate-instrument-keywords
  [instruments]
  (let [keywords (map iso/generate-title instruments)]
    (kws/generate-iso19115-descriptive-keywords "instrument" keywords)))

(defn generate-instruments
  "Returns content generator instructions for the given instruments."
  [instruments]
  (for [instrument instruments]
    [:gmi:instrument
     [(get-instrument-tag instrument) {:id (:instrument-id instrument)}
      [:gmi:citation
       [:gmd:CI_Citation
        [:gmd:title
         [:gco:CharacterString (iso/generate-title instrument)]]
        [:gmd:date {:gco:nilReason "unknown"}]]]
      [:gmi:identifier
       [:gmd:MD_Identifier
        [:gmd:code
         (char-string (:ShortName instrument))]
        [:gmd:description
         (char-string (:LongName instrument))]]]
      [:gmi:type
       [:gco:CharacterString (with-default (:Technique instrument))]]
      [:gmi:description {:gco:nilReason "missing"}]
      [:gmi:mountedOn {:xlink:href (str "#" (:platform-id instrument))}]
      (ch/generate-characteristics "instrumentInformation" (:Characteristics instrument))
      (sensor/generate-sensors (:ComposedOf instrument))]]))
