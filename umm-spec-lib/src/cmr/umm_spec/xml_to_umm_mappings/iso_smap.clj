(ns cmr.umm-spec.xml-to-umm-mappings.iso-smap
  "Defines mappings from ISO-SMAP XML to UMM records"
  (:require [cmr.umm-spec.json-schema :as js]
            [cmr.umm-spec.simple-xpath :refer [select]]
            [cmr.umm-spec.xml.parse :refer :all]
            [cmr.umm-spec.iso-smap-utils :as utils]))

(def md-identification-base-xpath
  (str "/gmd:DS_Series/gmd:seriesMetadata/gmi:MI_Metadata"
       "/gmd:identificationInfo/gmd:MD_DataIdentification"))

(def short-name-identification-xpath
  (str md-identification-base-xpath
       "[gmd:citation/gmd:CI_Citation/gmd:identifier/gmd:MD_Identifier"
       "/gmd:description/gco:CharacterString='The ECS Short Name']"))

(def entry-title-xpath
  (str md-identification-base-xpath
       "[gmd:citation/gmd:CI_Citation/gmd:title/gco:CharacterString='DataSetId']"
       "/gmd:aggregationInfo/gmd:MD_AggregateInformation/gmd:aggregateDataSetIdentifier"
       "/gmd:MD_Identifier/gmd:code/gco:CharacterString"))

;; Paths below are relative to the MD_DataIdentification element

(def entry-id-xpath
  (str "gmd:citation/gmd:CI_Citation/gmd:identifier/gmd:MD_Identifier"
       "[gmd:description/gco:CharacterString='The ECS Short Name']"
       "/gmd:code/gco:CharacterString"))

(def version-xpath
  (str "gmd:citation/gmd:CI_Citation/gmd:identifier/gmd:MD_Identifier"
       "[gmd:description/gco:CharacterString='The ECS Version ID']"
       "/gmd:code/gco:CharacterString"))

(def temporal-extent-xpath-str
  "gmd:extent/gmd:EX_Extent/gmd:temporalElement/gmd:EX_TemporalExtent/gmd:extent")

(def keywords-xpath-str
  "gmd:descriptiveKeywords/gmd:MD_Keywords/gmd:keyword/gco:CharacterString")

(defn iso-smap-xml-to-umm-c
  [doc]
  (let [data-id (first (select doc md-identification-base-xpath))
        short-name-el (first (select doc short-name-identification-xpath))]
    (js/coerce
     {:EntryId (value-of data-id entry-id-xpath)
      :EntryTitle (value-of doc entry-title-xpath)
      :Version (value-of data-id version-xpath)
      :Abstract (value-of short-name-el "gmd:abstract/gco:CharacterString")
      :Purpose (value-of short-name-el "gmd:purpose/gco:CharacterString")
      :CollectionProgress (value-of data-id "gmd:status/gmd:MD_ProgressCode")
      :DataLanguage (value-of short-name-el "gmd:language/gco:CharacterString")
      :Platforms (let [smap-keywords (values-at data-id keywords-xpath-str)]
                   (utils/parse-platforms smap-keywords))
      :TemporalExtents (for [temporal (select data-id temporal-extent-xpath-str)]
                         {:RangeDateTimes (for [period (select temporal "gml:TimePeriod")]
                                            {:BeginningDateTime (value-of period "gml:beginPosition")
                                             :EndingDateTime    (value-of period "gml:endPosition")})
                          :SingleDateTimes (values-at temporal "gml:TimeInstant/gml:timePosition")})})))
