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
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.iso-topic-categories :as iso-topic-categories]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.platform :as platform]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.project-element :as project]
   [cmr.umm-spec.xml-to-umm-mappings.iso-smap.data-contact :as data-contact]
   [cmr.umm-spec.xml-to-umm-mappings.iso-smap.distributions-related-url :as dru]
   [cmr.umm-spec.xml-to-umm-mappings.iso-smap.spatial :as spatial]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.tiling-system :as tiling]))

(def md-identification-base-xpath
  (str "/gmd:DS_Series/gmd:seriesMetadata/gmi:MI_Metadata"
       "/gmd:identificationInfo/gmd:MD_DataIdentification"))

(def citation-base-xpath
  (str md-identification-base-xpath
       "/gmd:citation/gmd:CI_Citation"))

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

(def projects-xpath
  (str "/gmd:DS_Series/gmd:seriesMetadata/gmi:MI_Metadata/gmi:acquisitionInformation/"
       "gmi:MI_AcquisitionInformation/gmi:operation/gmi:MI_Operation"))

(def base-xpath
  "/gmd:DS_Series/gmd:seriesMetadata")

(def collection-data-type-xpath
  (str citation-base-xpath
       "/gmd:identifier/gmd:MD_Identifier"
       "[gmd:codeSpace/gco:CharacterString='gov.nasa.esdis.umm.collectiondatatype']"
       "/gmd:code/gco:CharacterString"))

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

(defn- parse-doi
  "There could be multiple CI_Citations. Each CI_Citation could contain multiple gmd:identifiers.
   Each gmd:identifier could contain at most ONE DOI. The doi-list below will contain something like:
   [[nil]
    [nil {:DOI \"doi1\" :Authority \"auth1\"} {:DOI \"doi2\" :Authority \"auth2\"}]
    [{:DOI \"doi3\" :Authority \"auth3\"]]
   We will pick the first DOI for now."
  [doc]
  (let [orgname-path (str "gmd:MD_Identifier/gmd:authority/gmd:CI_Citation/gmd:citedResponsibleParty/"
                          "gmd:CI_ResponsibleParty/gmd:organisationName/gco:CharacterString")
        indname-path (str "gmd:MD_Identifier/gmd:authority/gmd:CI_Citation/gmd:citedResponsibleParty/"
                          "gmd:CI_ResponsibleParty/gmd:individualName/gco:CharacterString")
        doi-list (for [ci-ct (select doc citation-base-xpath)]
                   (for [gmd-id (select ci-ct "gmd:identifier")]
                     (when (and (= (value-of gmd-id "gmd:MD_Identifier/gmd:description/gco:CharacterString") "DOI")
                                (= (value-of gmd-id "gmd:MD_Identifier/gmd:codeSpace/gco:CharacterString")
                                   "gov.nasa.esdis.umm.doi"))
                       {:DOI (value-of gmd-id "gmd:MD_Identifier/gmd:code/gco:CharacterString")
                        :Authority (or (value-of gmd-id orgname-path)
                                       (value-of gmd-id orgname-path))})))]
    (first (first (remove empty? (map #(remove nil? %) doi-list))))))

(defn iso-smap-xml-to-umm-c
  "Returns UMM-C collection record from ISO-SMAP collection XML document. The :sanitize? option
  tells the parsing code to set the default values for fields when parsing the metadata into umm."
  [doc {:keys [sanitize?]}]
  (let [data-id-el (first (select doc md-identification-base-xpath))
        short-name-el (first (select doc short-name-identification-xpath))]
    (js/parse-umm-c
     (merge
      (data-contact/parse-contacts doc sanitize?) ; DataCenters, ContactPersons, ContactGroups
      {:ShortName (value-of data-id-el short-name-xpath)
       :EntryTitle (value-of doc entry-title-xpath)
       :ISOTopicCategories (iso-topic-categories/parse-iso-topic-categories doc base-xpath)
       :DOI (parse-doi doc)
       :Version (value-of data-id-el version-xpath)
       :Abstract (u/truncate (value-of short-name-el "gmd:abstract/gco:CharacterString") u/ABSTRACT_MAX sanitize?)
       :Purpose (u/truncate (value-of short-name-el "gmd:purpose/gco:CharacterString") u/PURPOSE_MAX sanitize?)
       :CollectionProgress (u/with-default (value-of data-id-el "gmd:status/gmd:MD_ProgressCode") sanitize?)
       :Quality (u/truncate (char-string-value doc quality-xpath) u/QUALITY_MAX sanitize?)
       :DataDates (iso-util/parse-data-dates doc data-dates-xpath)
       :DataLanguage (value-of short-name-el "gmd:language/gco:CharacterString")
       :Platforms (platform/parse-platforms doc base-xpath sanitize?)
       :TemporalExtents (or (seq (parse-temporal-extents data-id-el))
                            (when sanitize? u/not-provided-temporal-extents))
       :ScienceKeywords (parse-science-keywords data-id-el sanitize?)
       :LocationKeywords (kws/parse-location-keywords data-id-el)
       :SpatialExtent (spatial/parse-spatial data-id-el sanitize?)
       :TilingIdentificationSystems (tiling/parse-tiling-system data-id-el)
       :CollectionDataType (value-of (select doc collection-data-type-xpath) ".")
       ;; Required by UMM-C
       :ProcessingLevel {:Id
                         (u/with-default
                          (char-string-value
                           data-id-el "gmd:processingLevel/gmd:MD_Identifier/gmd:code")
                          sanitize?)
                         :ProcessingLevelDescription
                         (char-string-value
                          data-id-el "gmd:processingLevel/gmd:MD_Identifier/gmd:description")}
       :RelatedUrls (dru/parse-related-urls doc sanitize?)
       :Projects (project/parse-projects doc projects-xpath sanitize?)}))))
