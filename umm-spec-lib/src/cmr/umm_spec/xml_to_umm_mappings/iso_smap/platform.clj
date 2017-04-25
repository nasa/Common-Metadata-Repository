(ns cmr.umm-spec.xml-to-umm-mappings.iso-smap.platform
  "Functions for parsing UMM platform records out of ISO SMAP XML documents."
  (:require 
    [cmr.common.util :as util]
    [cmr.common.xml.parse :refer :all]
    [cmr.common.xml.simple-xpath :refer [select text]]
    [cmr.umm-spec.iso19115-2-util :as iso]
    [cmr.umm-spec.util :as su :refer [without-default-value-of]]
    [cmr.umm-spec.xml-to-umm-mappings.iso-smap.characteristics-and-operationalmodes :as char-and-opsmode]
    [cmr.umm-spec.xml-to-umm-mappings.iso-smap.instrument :as inst]))

(def platforms-xpath
  (str "/gmd:DS_Series/gmd:seriesMetadata/gmi:MI_Metadata/gmi:acquisitionInformation/gmi:MI_AcquisitionInformation/gmi:platform"
       "/eos:EOS_Platform"))

(defn xml-elem->platform
  [instruments-mapping platform-elem]
  (let [instrument-ids (keep #(get-in % [:attrs :xlink/href]) (select platform-elem "gmi:instrument"))
        instruments (seq (map (partial get instruments-mapping) instrument-ids))]
    (util/remove-nil-keys
      {:ShortName (value-of platform-elem iso/short-name-xpath)
       :LongName (value-of platform-elem iso/long-name-xpath)
       :Type (without-default-value-of platform-elem "gmi:description/gco:CharacterString")
       :Characteristics (char-and-opsmode/parse-characteristics platform-elem)
       :Instruments instruments})))

(defn parse-platforms
  "Returns the platforms parsed from the given xml document."
  [doc sanitize?]
  (let [instruments-mapping (inst/xml-elem->instruments-mapping doc)
        platforms (seq (map #(xml-elem->platform instruments-mapping %)
                            (select doc platforms-xpath)))]
    (or (seq platforms) (when sanitize? su/not-provided-platforms))))
