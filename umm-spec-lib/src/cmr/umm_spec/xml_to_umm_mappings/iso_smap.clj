(ns cmr.umm-spec.xml-to-umm-mappings.iso-smap
  "Defines mappings from ISO-SMAP XML to UMM records"
  (:require [cmr.umm-spec.json-schema :as js]
            [cmr.umm-spec.simple-xpath :refer [select]]
            [cmr.umm-spec.xml.parse :refer :all]
            [cmr.umm-spec.iso-keywords :as kws]
            [cmr.umm-spec.util :refer [without-default-value-of]]
            [cmr.umm-spec.xml-to-umm-mappings.iso-smap.spatial :as spatial]
            [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.tiling-system :as tiling]
            [cmr.umm-spec.iso19115-2-util :refer [umm-date-type-codes]]))

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

(def short-name-xpath
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

(def data-dates-xpath
  (str md-identification-base-xpath "/gmd:citation/gmd:CI_Citation/gmd:date/gmd:CI_Date"))

(defn- parse-science-keywords
  "Returns the parsed science keywords for the given ISO SMAP xml element. ISO-SMAP checks on the
  Category of each theme descriptive keyword to determine if it is a science keyword."
  [data-id-el]
  (->> data-id-el
       kws/parse-science-keywords
       (filter #(.contains kws/science-keyword-categories (:Category %)))))

(defn iso-smap-xml-to-umm-c
  [doc]
  (let [data-id-el (first (select doc md-identification-base-xpath))
        short-name-el (first (select doc short-name-identification-xpath))]
    (js/parse-umm-c
      {:ShortName (value-of data-id-el short-name-xpath)
       :EntryTitle (value-of doc entry-title-xpath)
       :Version (without-default-value-of data-id-el version-xpath)
       :Abstract (value-of short-name-el "gmd:abstract/gco:CharacterString")
       :Purpose (value-of short-name-el "gmd:purpose/gco:CharacterString")
       :CollectionProgress (value-of data-id-el "gmd:status/gmd:MD_ProgressCode")
       :DataDates (for [date-el (select doc data-dates-xpath)]
                    {:Date (value-of date-el "gmd:date/gco:DateTime")
                     :Type (get umm-date-type-codes (value-of date-el "gmd:dateType/gmd:CI_DateTypeCode"))})
       :DataLanguage (value-of short-name-el "gmd:language/gco:CharacterString")
       :Platforms (let [smap-keywords (values-at data-id-el keywords-xpath-str)]
                    (kws/parse-platforms smap-keywords))
       :TemporalExtents (for [temporal (select data-id-el temporal-extent-xpath-str)]
                          {:RangeDateTimes (for [period (select temporal "gml:TimePeriod")]
                                             {:BeginningDateTime (value-of period "gml:beginPosition")
                                              :EndingDateTime    (value-of period "gml:endPosition")})
                           :SingleDateTimes (values-at temporal "gml:TimeInstant/gml:timePosition")})
       :ScienceKeywords (parse-science-keywords data-id-el)
       :SpatialExtent (spatial/parse-spatial data-id-el)
       :TilingIdentificationSystem (tiling/parse-tiling-system data-id-el)})))
