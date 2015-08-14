(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2
  "Defines mappings from ISO19115-2 XML to UMM records"
  (:require [cmr.umm-spec.xml-to-umm-mappings.dsl :refer :all]
            [cmr.umm-spec.xml-to-umm-mappings.add-parse-type :as apt]
            [cmr.umm-spec.json-schema :as js]))

(def data-identification-base-xpath
  "/gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification")

(def citation-base-xpath
  (str data-identification-base-xpath "/gmd:citation/gmd:CI_Citation"))

(def entry-id-xpath
  (xpath (str citation-base-xpath "/gmd:identifier/gmd:MD_Identifier/gmd:code/gco:CharacterString")))

(def entry-title-xpath
  (xpath (str citation-base-xpath "/gmd:title/gco:CharacterString")))

(def iso19115-2-xml-to-umm-c
  (apt/add-parsing-types
    js/umm-c-schema
    (object {:EntryId (object {:Id entry-id-xpath})
             :EntryTitle entry-title-xpath
             :Abstract (xpath (str data-identification-base-xpath "/gmd:abstract/gco:CharacterString"))
             :Purpose (xpath (str data-identification-base-xpath "/gmd:purpose/gco:CharacterString"))
             })))