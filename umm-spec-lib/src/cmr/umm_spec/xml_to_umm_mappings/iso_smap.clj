(ns cmr.umm-spec.xml-to-umm-mappings.iso-smap
  "Defines mappings from ISO-SMAP XML to UMM records"
  (:require
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.umm-spec.iso-keywords :as kws]
   [cmr.umm-spec.iso19115-2-util :as iso-util :refer [char-string-value]]
   [cmr.umm-spec.json-schema :as js]
   [cmr.umm-spec.util :as u :refer [without-default-value-of]]
   [cmr.umm-spec.util :as u]
   [cmr.umm-spec.xml-to-umm-mappings.iso-smap.spatial :as spatial]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.tiling-system :as tiling]))

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

(def quality-xpath
  (str "/gmd:DS_Series/gmd:seriesMetadata/gmi:MI_Metadata/gmd:dataQualityInfo/gmd:DQ_DataQuality"
       "/gmd:report/DQ_QuantitativeAttributeAccuracy/gmd:evaluationMethodDescription"))

(def data-dates-xpath
  (str md-identification-base-xpath "/gmd:citation/gmd:CI_Citation/gmd:date/gmd:CI_Date"))

(defn- parse-science-keywords
  "Returns the parsed science keywords for the given ISO SMAP xml element. ISO-SMAP checks on the
  Category of each theme descriptive keyword to determine if it is a science keyword."
  [data-id-el sanitize?]
  (if-let [science-keywords (seq
                              (->> (kws/parse-science-keywords data-id-el sanitize?)
                                   (filter #(.contains kws/science-keyword-categories (:Category %)))))]
    science-keywords
    (when sanitize?
      u/not-provided-science-keywords)))

(defn parse-temporal-extents
  "Parses the collection temporal extents from the data identification element"
  [data-id-el]
  (for [temporal (select data-id-el temporal-extent-xpath-str)]
    {:RangeDateTimes (for [period (select temporal "gml:TimePeriod")]
                       {:BeginningDateTime (value-of period "gml:beginPosition")
                        :EndingDateTime    (value-of period "gml:endPosition")})
     :SingleDateTimes (values-at temporal "gml:TimeInstant/gml:timePosition")
     :EndsAtPresentFlag (some? (seq (select temporal "gml:TimePeriod/gml:endPosition[@indeterminatePosition='now']")))}))

(defn iso-smap-xml-to-umm-c
  "Returns UMM-C collection record from ISO-SMAP collection XML document. The :sanitize? option
  tells the parsing code to set the default values for fields when parsing the metadata into umm."
  [doc {:keys [sanitize?]}]
  (let [data-id-el (first (select doc md-identification-base-xpath))
        short-name-el (first (select doc short-name-identification-xpath))]
    (js/parse-umm-c
      {:ShortName (u/truncate (value-of data-id-el short-name-xpath) u/SHORTNAME_MAX sanitize?)
       :EntryTitle (value-of doc entry-title-xpath)
       :Version (value-of data-id-el version-xpath)
       :Abstract (u/truncate (value-of short-name-el "gmd:abstract/gco:CharacterString") u/ABSTRACT_MAX sanitize?)
       :Purpose (u/truncate (value-of short-name-el "gmd:purpose/gco:CharacterString") u/PURPOSE_MAX sanitize?)
       :CollectionProgress (value-of data-id-el "gmd:status/gmd:MD_ProgressCode")
       :Quality (u/truncate (char-string-value doc quality-xpath) u/QUALITY_MAX sanitize?)
       :DataDates (iso-util/parse-data-dates doc data-dates-xpath)
       :DataLanguage (value-of short-name-el "gmd:language/gco:CharacterString")
       :Platforms (let [smap-keywords (values-at data-id-el keywords-xpath-str)]
                    (kws/parse-platforms smap-keywords))
       :TemporalExtents (or (seq (parse-temporal-extents data-id-el))
                            (when sanitize? u/not-provided-temporal-extents))
       :ScienceKeywords (parse-science-keywords data-id-el sanitize?)
       :SpatialExtent (spatial/parse-spatial data-id-el sanitize?)
       :TilingIdentificationSystems (tiling/parse-tiling-system data-id-el)
       ;; Required by UMM-C
       :RelatedUrls (when sanitize? [u/not-provided-related-url])
       ;; Required by UMM-C
       :ProcessingLevel (when sanitize? {:Id u/not-provided})
       ;; DataCenters is not implemented but is required in UMM-C
       :DataCenters (when sanitize? [u/not-provided-data-center])})))
