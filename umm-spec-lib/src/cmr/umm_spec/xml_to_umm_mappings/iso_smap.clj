(ns cmr.umm-spec.xml-to-umm-mappings.iso-smap
  "Defines mappings from ISO-SMAP XML to UMM records"
  (:require [cmr.umm-spec.xml-to-umm-mappings.dsl :refer :all]
            [cmr.umm-spec.xml-to-umm-mappings.add-parse-type :as apt]
            [cmr.umm-spec.json-schema :as js]))

(def metadata-base-xpath
  "/gmd:DS_Series/gmd:seriesMetadata/gmi:MI_Metadata")

(def entry-id-xpath
  ;; For now, we set the entry-id to short-name
  (xpath (str metadata-base-xpath
              "/gmd:identificationInfo/gmd:MD_DataIdentification"
              "/gmd:citation/gmd:CI_Citation/gmd:identifier/gmd:MD_Identifier"
              "[gmd:description/gco:CharacterString='The ECS Short Name']"
              "/gmd:code/gco:CharacterString")))

(def short-name-identification-xpath
  (str metadata-base-xpath
       "/gmd:identificationInfo/gmd:MD_DataIdentification"
       "[gmd:citation/gmd:CI_Citation/gmd:identifier/gmd:MD_Identifier"
       "/gmd:description/gco:CharacterString='The ECS Short Name']"))

(def abstract-xpath
  (xpath (str short-name-identification-xpath
              "/gmd:abstract/gco:CharacterString")))

(def purpose-xpath
  (xpath (str short-name-identification-xpath
              "/gmd:purpose/gco:CharacterString")))

(def data-language-xpath
  (xpath (str short-name-identification-xpath
              "/gmd:language/gco:CharacterString")))

(def entry-title-xpath
  (xpath (str metadata-base-xpath
              "/gmd:identificationInfo/gmd:MD_DataIdentification"
              "[gmd:citation/gmd:CI_Citation/gmd:title/gco:CharacterString='DataSetId']"
              "/gmd:aggregationInfo/gmd:MD_AggregateInformation/gmd:aggregateDataSetIdentifier"
              "/gmd:MD_Identifier/gmd:code/gco:CharacterString")))

(def temporal-extent-xpath-str
  (str metadata-base-xpath
       "/gmd:identificationInfo/gmd:MD_DataIdentification"
       "/gmd:extent/gmd:EX_Extent"
       "/gmd:temporalElement/gmd:EX_TemporalExtent/gmd:extent"))

(def iso-smap-xml-to-umm-c
  (apt/add-parsing-types
    js/umm-c-schema
    (object {:EntryId entry-id-xpath
             :EntryTitle entry-title-xpath
             :Abstract abstract-xpath
             :Purpose purpose-xpath
             :DataLanguage data-language-xpath
             :TemporalExtents (for-each temporal-extent-xpath-str
                                (object {:RangeDateTimes (for-each "gml:TimePeriod"
                                                           (object {:BeginningDateTime (xpath "gml:beginPosition")
                                                                    :EndingDateTime    (xpath "gml:endPosition")}))
                                         :SingleDateTimes (select "gml:TimeInstant/gml:timePosition")}))})))
