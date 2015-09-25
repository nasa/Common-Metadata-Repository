(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2.platform
  "Functions for generating ISO19115-2 XML elements from UMM platform records."
  (:require [cmr.umm-spec.xml.gen :refer :all]
            [cmr.umm-spec.iso19115-util :as iso]
            [cmr.umm-spec.util :refer [with-default]]
            [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.instrument :as inst]
            [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.characteristics :as ch]))

(defn- platform-with-id
  "Returns the platform with generated ids for ISO xml generation"
  [platform]
  (let [platform-id (iso/generate-id)
        instruments (inst/instruments-with-id (:Instruments platform) platform-id)]
    (-> platform
        (assoc :Instruments instruments)
        (assoc :platform-id platform-id))))

(defn platforms-with-id
  "Returns the platforms with generated ids for ISO xml generation"
  [platforms]
  (map platform-with-id platforms))

(defn generate-platform-keywords
  [platforms]
  (let [keywords (map iso/generate-title platforms)]
    (iso/generate-descriptive-keywords "platform" keywords)))

(defn generate-instrument-keywords
  [platforms]
  (inst/generate-instrument-keywords (mapcat :Instruments platforms)))

(defn generate-instruments
  [platforms]
  (inst/generate-instruments (mapcat :Instruments platforms)))

(defn generate-platforms
  "Returns the content generator instructions for the given platforms."
  [platforms]
  (for [platform platforms]
    [:gmi:platform
     [:eos:EOS_Platform {:id (:platform-id platform)}
      [:gmi:identifier
       [:gmd:MD_Identifier
        [:gmd:code
         (char-string (:ShortName platform))]
        [:gmd:description
         (char-string (:LongName platform))]]]
      [:gmi:description
       [:gco:CharacterString (with-default (:Type platform))]]
      ;; Instrument links
      (if-let [instruments (:Instruments platform)]
        (for [instrument instruments]
          [:gmi:instrument {:xlink:href (str "#" (:instrument-id instrument))}])
        [:gmi:instrument {:gco:nilReason "inapplicable"}])

      ;; Characteristics
      (when-let [characteristics (:Characteristics platform)]
        iso/echo-attributes-info
        (ch/generate-characteristics "platformInformation" characteristics))]]))