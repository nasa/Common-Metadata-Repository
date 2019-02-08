(ns cmr.umm-spec.xml-to-umm-mappings.iso-shared.platform
  "Functions for parsing UMM platform records out of ISO SMAP XML documents."
  (:require
   [clojure.string :as string]
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
  ([doc base-xpath instruments-mapping platform-elem]
   (let [platform-id (get-in platform-elem [:attrs :id])
         instrument-ids (keep #(get-in % [:attrs :xlink/href]) (select platform-elem "gmi:instrument"))
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
  ([doc base-xpath platform-elem]
    ;; This is the case when platform-elem is from alternative path. This platform will only contain ShortName and LongName.
   (when-let [short-long-name (value-of platform-elem iso/short-name-xpath)]
    (let [short-long-name-list (string/split short-long-name #">")]
      (util/remove-nil-keys
        {:ShortName (string/trim (first short-long-name-list))
         :LongName (when-let [long-name (second short-long-name-list)]
                     (string/trim long-name))})))))

(defn parse-platforms
  "Returns the platforms parsed from the given xml document."
  ([doc base-xpath sanitize?]
   ;; This is iso-smap case, where alternative xpath for instruments and platforms doesn't apply.
   (let [instruments-mapping (inst/xml-elem->instruments-mapping doc base-xpath)
         platforms (seq (map #(xml-elem->platform doc base-xpath instruments-mapping %)
                             (select doc (str base-xpath platforms-xpath))))]
     (or (seq platforms) (when sanitize? su/not-provided-platforms))))
  ([doc base-xpath sanitize? alt-xpath-options]
   ;; This is isomends case where alternative xpath exist for both instruments and platforms.
   ;; Note: I'm leaving this parse-platforms function in iso_shared because the current implementation
   ;; for isomends alternative xpath is just temporary. Once the relationship between the platforms
   ;; and instruments is established, it will be changed.
   (let [inst-alt-xpath (:inst-alt-xpath alt-xpath-options)
         plat-alt-xpath (:plat-alt-xpath alt-xpath-options)
         plat-elems (select doc (str base-xpath platforms-xpath))
         instruments-mapping (inst/xml-elem->instruments-mapping doc base-xpath inst-alt-xpath)
         platforms (if (or (map? instruments-mapping) (nil? (seq instruments-mapping)))
                     (seq (map #(xml-elem->platform doc base-xpath instruments-mapping %) plat-elems))
                     ;; NOAA case when instruments are not associated with any platforms.
                     ;; Platforms are the combination of platforms from alternative xpath
                     ;; and the not-provided-platforms that contain the instruments from alternative xpath.
                     (let [plat-elems (select doc (str base-xpath plat-alt-xpath))
                           plats-alt-xpath (seq (map #(xml-elem->platform doc base-xpath %) plat-elems))
                           not-provided-plats (seq (map #(assoc % :Instruments instruments-mapping)
                                                        su/not-provided-platforms))]
                       (into [] (concat plats-alt-xpath not-provided-plats))))]
     (or (seq platforms) (when sanitize? su/not-provided-platforms)))))
