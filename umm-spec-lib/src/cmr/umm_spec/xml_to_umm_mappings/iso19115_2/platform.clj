(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2.platform
  "Functions for parsing UMM platform records out of ISO 19115-2 XML documents."
  (:require [cmr.umm-spec.simple-xpath :refer [select text]]
            [cmr.umm-spec.xml.parse :refer :all]
            [cmr.umm-spec.iso19115-util :as iso]
            [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.instrument :as inst]
            [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.characteristics :as ch]))

(def platforms-xpath
  (str "/gmi:MI_Metadata/gmi:acquisitionInformation/gmi:MI_AcquisitionInformation/gmi:platform"
       "/eos:EOS_Platform"))

(defn xml-elem->platform
  [instruments-mapping platform-elem]
  (let [instrument-ids (keep #(get-in % [:attrs :xlink/href]) (select platform-elem "gmi:instrument"))
        instruments (seq (map (partial get instruments-mapping) instrument-ids))]
    {:ShortName (value-of platform-elem iso/short-name-xpath)
     :LongName (value-of platform-elem iso/long-name-xpath)
     :Type (iso/char-string-value platform-elem "gmi:description")
     :Characteristics (ch/parse-characteristics platform-elem)
     :Instruments instruments}))

(defn parse-platforms
  "Returns the platforms parsed from the given xml document."
  [doc]
  (let [instruments-mapping (inst/xml-elem->instruments-mapping doc)]
    (seq (map (partial xml-elem->platform instruments-mapping)
              (select doc platforms-xpath)))))
