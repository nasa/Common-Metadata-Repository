(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2.metadata-association
  "Functions for parsing UMM metadata association records out of ISO 19115-2 XML elemuments."
  (:require [cmr.umm-spec.simple-xpath :refer [select text]]
            [cmr.umm-spec.xml.parse :refer :all]
            [cmr.umm-spec.util :refer [without-default-value-of]]
            [cmr.umm-spec.iso19115-2-util :as iso]))


(def non-source-am-xpath
  "Non-source associated metadata xpath relative to md-data-id-base-xpath"
  "gmd:aggregationInfo/gmd:MD_AggregateInformation")


(def cit-prefix
  "Prefix for elements under the metadata assocations non-source citations"
  "gmd:aggregateDataSetName/gmd:CI_Citation")

(defn xml-elem->metadata-associations
  "Returns the metadata associations by parsing the given xml element"
  [elem]
  (cmr.common.dev.capture-reveal/capture-all)
  (concat
    (for [ma (select elem non-source-am-xpath) :when (seq (select ma "gmd:associationType"))
          :let [assoc-type (value-of ma "gmd:associationType/gmd:DS_AssociationTypeCode")]
          :when (contains? #{"SCIENCE ASSOCIATED" "DEPENDENT"} assoc-type)]
      {:EntryId (iso/char-string-value
                  ma (str cit-prefix "/gmd:title"))
       :Version (iso/char-string-value
                  ma (str cit-prefix "/gmd:edition"))
       :Description (iso/char-string-value
                      ma (str cit-prefix "/gmd:otherCitationDetails"))
       :Type assoc-type})))