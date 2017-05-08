(ns cmr.umm-spec.umm-to-xml-mappings.iso-shared.platform
  "Functions for generating ISOSMAP XML elements from UMM platform records."
  (:require
    [cmr.common.xml.gen :refer :all]
    [cmr.umm-spec.iso19115-2-util :as iso]
    [cmr.umm-spec.iso-keywords :as kws]
    [cmr.umm-spec.umm-to-xml-mappings.iso-shared.instrument :as inst]
    [cmr.umm-spec.umm-to-xml-mappings.iso-shared.characteristics-and-operationalmodes :as ch]
    [cmr.umm-spec.util :as su :refer [with-default char-string]]))

(defn- platform-with-id
  "Returns the platform with generated ids for ISO xml generation"
  [platform]
  (let [platform-id (su/generate-id)
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
    (kws/generate-iso-smap-descriptive-keywords "platform" keywords)))

(defn generate-instrument-keywords
  [platforms]
  (inst/generate-instrument-keywords (mapcat :Instruments platforms)))

(defn generate-instruments
  [platforms]
  (inst/generate-instruments (mapcat :Instruments platforms)))

(defn generate-child-instruments
  [platforms]
  (inst/generate-child-instruments (mapcat :Instruments platforms)))

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
        [:gmd:codeSpace
         (char-string "gov.nasa.esdis.umm.platformshortname")]
        [:gmd:description
         (char-string (:LongName platform))]]]
      [:gmi:description
       [:gco:CharacterString (with-default (:Type platform))]]
      ;; Instrument links
      (if-let [instruments (seq (:Instruments platform))]
        (for [instrument instruments]
          [:gmi:instrument {:xlink:href (str "#" (:instrument-id instrument))}])
        [:gmi:instrument {:gco:nilReason "inapplicable"}])

      ;; Characteristics
      (when-let [characteristics (:Characteristics platform)]
        iso/eos-echo-attributes-info
        (ch/generate-characteristics "platformInformation" characteristics))]]))
