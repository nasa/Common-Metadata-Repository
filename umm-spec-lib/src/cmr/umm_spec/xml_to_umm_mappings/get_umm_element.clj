(ns cmr.umm-spec.xml-to-umm-mappings.get-umm-element
  "Functions for getting UMM element out of XML documents of other formats."
  (:require
   [clojure.string :as string]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer :all]
   [cmr.umm-spec.util :as umm-spec-util]))

(defn get-collection-progress
  "Get collection progress value of UMM based on coll-progress-mapping and xml-path
   If the upper case of the xml-value at xml-path is mapped to a value in coll-progress-mapping,
   that value will be used for collection progress. Otherwise, use \"NOT PROVIDED\""
  [coll-progress-mapping doc xml-path sanitize?]
  (get coll-progress-mapping
       (when-let [xml-value (value-of doc xml-path)]
         (string/upper-case xml-value))
       (when sanitize?
         umm-spec-util/NOT-PROVIDED)))
