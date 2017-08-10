(ns cmr.umm-spec.umm-to-xml-mappings.iso-smap.collection-citation
  "Functions for generating ISO XML elements from UMM collection-citation records."
  (:require
    [cmr.common.xml.gen :refer :all]
    [cmr.umm-spec.util :as util]))

;; The Title and Version of the collection citation are sharing the same xml elements as
;; EntryTitle and Version of umm in iso19115.  And the xml elements are populated by the
;; EntryTitle and Version of umm for iso19115. Not the case for iso-smap, so still need
;; to convert title and version in collection citation for iso-smap.
(defn convert-title
  "Convert the tiltle in umm to the related field in xml."
  [c]
  (let [title (:Title (first (:CollectionCitations c)))
        title-sanitized (if title
                          title
                          util/not-provided)]
    [:gmd:title
     [:gco:CharacterString title-sanitized]]))

(defn convert-version
  "Convert the tiltle in umm to the related field in xml."
  [c]
  (when-let [version (:Version (first (:CollectionCitations c)))]
    [:gmd:edition
     [:gco:CharacterString version]]))

