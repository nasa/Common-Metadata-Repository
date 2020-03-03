(ns cmr.umm-spec.umm-to-xml-mappings.iso-shared.collection-progress
  "Functions for generating ISO XML elements from UMM collection-progress records."
  (:require
    [clojure.string :as string]
    [cmr.common.xml.gen :refer :all]
    [cmr.umm-spec.iso19115-2-util :as iso]))

(def coll-progress-mapping
  "Mapping from known collection progress values to values supported for ISO ProgressCode."
  {"COMPLETE" "completed"
   "ACTIVE" "onGoing"
   "PLANNED" "planned"
   "DEPRECATED" "deprecated"
   "NOT APPLICABLE" "NOT APPLICABLE"})

(defn generate-collection-progress
  "Returns ISO CollectionProgress element from UMM-C collection c."
  [c]
  (when-let [c-progress (when-let [coll-progress (:CollectionProgress c)]
                          (get coll-progress-mapping (string/upper-case coll-progress)))]
    [:gmd:status (if (= "NOT APPLICABLE" c-progress)
                   [:gmd:MD_ProgressCode
                     {:codeList ""
                      :codeListValue ""}
                    c-progress]
                   [:gmd:MD_ProgressCode
                     {:codeList (str (:ngdc iso/code-lists) "#MD_ProgressCode")
                      :codeListValue c-progress}
                    c-progress])]))
