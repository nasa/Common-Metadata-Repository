(ns cmr.umm-spec.xml-to-umm-mappings.iso-shared.platform
  "Functions for parsing UMM platform records out of ISO SMAP XML documents."
  (:require
    [cmr.common.util :as util]
    [cmr.common.xml.parse :refer :all]
    [cmr.common.xml.simple-xpath :refer [select text]]
    [cmr.umm-spec.iso19115-2-util :as iso]
    [cmr.umm-spec.util :as su :refer [without-default-value-of]]
    [cmr.umm-spec.xml-to-umm-mappings.iso-shared.characteristics-and-operationalmodes :as char-and-opsmode]
    [cmr.umm-spec.xml-to-umm-mappings.iso-shared.instrument :as inst]))

(def platforms-xpath
  (str "/gmi:MI_Metadata/gmi:acquisitionInformation/gmi:MI_AcquisitionInformation/gmi:platform"
       "/eos:EOS_Platform"))

(defn xml-elem->platform
  "Returns platform record using the platform element and instrument-id/instrument-record mapping"
  [doc base-xpath instruments-mapping platform-elem]
  (let [platform-id (get-in platform-elem [:attrs :id])
        instrument-ids (keep #(get-in % [:attrs :xlink/href]) (select platform-elem "gmi:instrument"))
        instrument-sub-elems (map
                              #(inst/xml-elem->instrument doc base-xpath %)
                              (select platform-elem "gmi:instrument/eos:EOS_Instrument"))
        instruments (->> (concat
                          (map (partial get instruments-mapping) instrument-ids)
                          (filter #(= platform-id (:mounted-on-id %)) (vals instruments-mapping)))
                         (map #(dissoc % :mounted-on-id))
                         distinct
                         seq)]
    (util/remove-nil-keys
      {:ShortName (value-of platform-elem iso/short-name-xpath)
       :LongName (value-of platform-elem iso/long-name-xpath)
       :Type (without-default-value-of platform-elem "gmi:description/gco:CharacterString")
       :Characteristics (char-and-opsmode/parse-characteristics platform-elem)
       :Instruments instruments})))

(defn parse-platforms
  "Returns the platforms parsed from the given xml document."
  [doc base-xpath sanitize?]
  (let [instruments-mapping (inst/xml-elem->instruments-mapping doc base-xpath)
        platforms (seq (map #(xml-elem->platform doc base-xpath instruments-mapping %)
                            (select doc (str base-xpath platforms-xpath))))]
    (or (seq platforms) (when sanitize? su/not-provided-platforms))))
