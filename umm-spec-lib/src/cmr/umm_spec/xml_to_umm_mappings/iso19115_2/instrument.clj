(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2.instrument
  "Functions for parsing UMM instrument records out of ISO 19115-2 XML documents."
  (:require [cmr.common.xml.simple-xpath :refer [select text]]
            [cmr.common.xml.parse :refer :all]
            [cmr.umm-spec.util :refer [without-default-value-of]]
            [cmr.umm-spec.iso19115-2-util :as iso :refer [char-string-value]]
            [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.characteristics :as ch]))

(def instruments-xpath
  "/gmi:MI_Metadata/gmi:acquisitionInformation/gmi:MI_AcquisitionInformation/gmi:instrument")

(defn- parse-instrument-sensors
  "Returns the parsed instrument sensors from the instrument element."
  [instrument]
  (for [sensor (select instrument "eos:sensor/eos:EOS_Sensor")]
    {:ShortName (char-string-value sensor "eos:identifier/gmd:MD_Identifier/gmd:code")
     :LongName (char-string-value sensor "eos:identifier/gmd:MD_Identifier/gmd:description")
     :Technique (without-default-value-of sensor "eos:type/gco:CharacterString")
     :Characteristics (ch/parse-characteristics sensor)}))

(defn- xml-elem->instrument
  [instrument-elem]
  {:ShortName (value-of instrument-elem iso/short-name-xpath)
   :LongName (value-of instrument-elem iso/long-name-xpath)
   :Technique (without-default-value-of instrument-elem "gmi:type/gco:CharacterString")
   :Characteristics (ch/parse-characteristics instrument-elem)
   :ComposedOf (parse-instrument-sensors instrument-elem)})

(defn- xml-elem->instrument-mapping
  [instrument-elem]
  ;; the instrument element could be under tag of either :EOS_Instrument or :MI_Instrument
  ;; here we just parse the :content to avoid using two different xpaths.
  (let [instrument-elem (first (:content instrument-elem))
        id (get-in instrument-elem [:attrs :id])
        instrument (xml-elem->instrument instrument-elem)]
    [(str "#" id) instrument]))

(defn xml-elem->instruments-mapping
  "Returns the instrument id to Instrument mapping by parsing the given xml element"
  [doc]
  (into {}
        (map xml-elem->instrument-mapping (select doc instruments-xpath))))
