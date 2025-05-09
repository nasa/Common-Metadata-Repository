(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2.metadata-association
  "Functions for parsing UMM metadata association records out of ISO 19115-2 XML elemuments."
  (:require
   [clojure.string :as string]
   [cmr.common.xml.parse :refer [value-of]]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.umm-spec.iso19115-2-util :as iso]
   [cmr.common.util :as util]))

(def source-ma-xpath
  "Source associated metadata xpath relative to document root xpath"
  (str "/gmi:MI_Metadata/gmd:dataQualityInfo/:gmd:DQ_DataQuality/:gmd:lineage/:gmd:LI_Lineage"
       "/gmd:source/gmd:LI_Source"))

(def non-source-ma-xpath
  "Non-source associated metadata xpath relative to document root xpath"
  (str "/gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification"
       "/gmd:aggregationInfo/gmd:MD_AggregateInformation"))

(def citation-prefix
  "Prefix for elements under the metadata assocations non-source citations"
  "gmd:aggregateDataSetName/gmd:CI_Citation")

(def non-associated-metadata-types
  "A list of other associated Types other than associated-metadata used to find associated-metadata data."
  ["crossreference" "associateddoi" "childdataset" "collaborativeotheragency" "fieldcampaign" "parentdataset" "relateddataset" "other" "ispreviousversionof" "isnewversionof" "doipreviousversion" "isdescribedby"])

(defn xml-elem->metadata-associations
  "Returns the metadata associations by parsing the given xml element"
  [elem]
  (concat
    (for [ma (select elem non-source-ma-xpath)
          :let [assoc-code-list (select ma "gmd:associationType/gmd:DS_AssociationTypeCode")
                assoc-type (value-of (first assoc-code-list) "@codeListValue")
                assoc-type (when assoc-type
                             (string/trim assoc-type))
                l-assoc-type (util/safe-lowercase assoc-type)]
          ;; crossReference type is used by publications
          ;; associatedDOIs is used by associatedDOIs
          :when (or (nil? assoc-type)
                    (not (some #(= l-assoc-type %) non-associated-metadata-types)))]
      {:EntryId (iso/char-string-value
                  ma (str citation-prefix "/gmd:title"))
       :Version (iso/char-string-value
                  ma (str citation-prefix "/gmd:edition"))
       :Description (iso/char-string-value
                      ma (str citation-prefix "/gmd:otherCitationDetails"))
       :Type (some-> assoc-type
                     string/upper-case)})
    (for [ma (select elem source-ma-xpath)]
      {:EntryId (iso/char-string-value ma "gmd:sourceCitation/gmd:CI_Citation/gmd:title")
       :Version (iso/char-string-value ma "gmd:sourceCitation/gmd:CI_Citation/gmd:edition")
       :Description (iso/char-string-value ma "gmd:description")
       :Type "INPUT"})))
