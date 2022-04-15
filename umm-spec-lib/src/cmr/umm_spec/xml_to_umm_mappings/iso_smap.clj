(ns cmr.umm-spec.xml-to-umm-mappings.iso-smap
  "Defines mappings from ISO-SMAP XML to UMM records"
  (:require
   [clojure.string :as string]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.common.util :as util]
   [cmr.umm-spec.iso-keywords :as kws]
   [cmr.umm-spec.iso19115-2-util :as iso-util :refer [char-string-value]]
   [cmr.umm-spec.json-schema :as js]
   [cmr.umm-spec.models.umm-collection-models :as umm-c]
   [cmr.umm-spec.util :as u :refer [without-default-value-of]]
   [cmr.umm-spec.util :as u]
   [cmr.umm-spec.xml-to-umm-mappings.get-umm-element :as get-umm-element]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.archive-and-dist-info :as archive-and-dist-info]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.collection-citation :as collection-citation]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.doi :as doi]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.iso-topic-categories :as iso-topic-categories]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.platform :as platform]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.project-element :as project]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.use-constraints :as use-constraints]
   [cmr.umm-spec.xml-to-umm-mappings.iso-smap.data-contact :as data-contact]
   [cmr.umm-spec.xml-to-umm-mappings.iso-smap.distributions-related-url :as dru]
   [cmr.umm-spec.xml-to-umm-mappings.iso-smap.spatial :as spatial]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.tiling-system :as tiling]
   [cmr.umm-spec.versioning :as umm-spec-versioning]))

(def coll-progress-mapping
  "Mapping from values supported for ISO-SMAP ProgressCode to UMM CollectionProgress."
  {"COMPLETED" "COMPLETE"
   "HISTORICALARCHIVE" "DEPRECATED"
   "OBSOLETE" "DEPRECATED"
   "RETIRED" "DEPRECATED"
   "DEPRECATED" "DEPRECATED"
   "ONGOING" "ACTIVE"
   "PLANNED" "PLANNED"
   "UNDERDEVELOPMENT" "PLANNED"
   "NOT APPLICABLE" "NOT APPLICABLE"})

(def md-identification-base-xpath
  (str "/gmd:DS_Series/gmd:seriesMetadata/gmi:MI_Metadata"
       "/gmd:identificationInfo/gmd:MD_DataIdentification"))

(def constraints-xpath
  (str md-identification-base-xpath "/gmd:resourceConstraints/gmd:MD_LegalConstraints"))

(def citation-base-xpath
  (str md-identification-base-xpath
       "/gmd:citation/gmd:CI_Citation"))

(def collection-citation-base-xpath
  (str citation-base-xpath
       "[gmd:identifier/gmd:MD_Identifier"
       "/gmd:description/gco:CharacterString='The ECS Short Name']"))

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

(def archive-info-xpath
  (str base-xpath
       "/gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:resourceFormat"))

(def dist-info-xpath
  (str base-xpath
       "/gmi:MI_Metadata/gmd:distributionInfo/gmd:MD_Distribution"))

(def spatial-extent-xpath
  (str md-identification-base-xpath "/gmd:extent/gmd:EX_Extent"))

(def associated-doi-xpath
  (str md-identification-base-xpath "/gmd:aggregationInfo/gmd:MD_AggregateInformation/"))

(defn- parse-science-keywords
  "Returns the parsed science keywords for the given ISO SMAP xml element. ISO-SMAP checks on the
  Category of each theme descriptive keyword to determine if it is a science keyword."
  [data-id-el sanitize?]
  (if-let [science-keywords (seq
                              ;; kws/parse-science-keywords is shared by iso19115 and isosmap
                              ;; "true" indicates it's isosmap case.
                              (->> (kws/parse-science-keywords data-id-el sanitize? true)
                                   (filter #(.contains kws/science-keyword-categories (:Category %)))))]
    science-keywords
    (when sanitize?
      u/not-provided-science-keywords)))

(defn parse-temporal-extents
  "Parses the collection temporal extents from the data identification element"
  [data-id-el]
  (util/doall-recursive
   (for [temporal (select data-id-el temporal-extent-xpath-str)]
     {:RangeDateTimes (for [period (select temporal "gml:TimePeriod")]
                        {:BeginningDateTime (date-at-str period "gml:beginPosition")
                         :EndingDateTime    (date-at-str period "gml:endPosition")})
      :SingleDateTimes (dates-at-str temporal "gml:TimeInstant/gml:timePosition")
      :EndsAtPresentFlag (some? (seq (select temporal "gml:TimePeriod/gml:endPosition[@indeterminatePosition='now']")))})))

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
       :DOI (doi/parse-doi doc citation-base-xpath)
       :AssociatedDOIs (doi/parse-associated-dois doc associated-doi-xpath)
       :Version (value-of data-id-el version-xpath)
       :Abstract (u/truncate (value-of short-name-el "gmd:abstract/gco:CharacterString") u/ABSTRACT_MAX sanitize?)
       :Purpose (u/truncate (value-of short-name-el "gmd:purpose/gco:CharacterString") u/PURPOSE_MAX sanitize?)
       :CollectionProgress (get-umm-element/get-collection-progress
                             coll-progress-mapping
                             data-id-el
                             "gmd:status/gmd:MD_ProgressCode"
                             sanitize?)
       :Quality (u/truncate (char-string-value doc quality-xpath) u/QUALITY_MAX sanitize?)
       :DataDates (iso-util/parse-data-dates doc data-dates-xpath)
       :AccessConstraints (use-constraints/parse-access-constraints doc constraints-xpath sanitize?)
       :UseConstraints (use-constraints/parse-use-constraints doc constraints-xpath sanitize?)
       :DataLanguage (value-of short-name-el "gmd:language/gco:CharacterString")
       :Platforms (platform/parse-platforms doc base-xpath sanitize?)
       :TemporalExtents (or (seq (parse-temporal-extents data-id-el))
                            (when sanitize? u/not-provided-temporal-extents))
       :ScienceKeywords (parse-science-keywords data-id-el sanitize?)
       :LocationKeywords (kws/parse-location-keywords data-id-el)
       :SpatialExtent (spatial/parse-spatial doc data-id-el spatial-extent-xpath sanitize?)
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
       :CollectionCitations (collection-citation/parse-collection-citation doc collection-citation-base-xpath sanitize?)
       :Projects (project/parse-projects doc projects-xpath sanitize?)
       :ArchiveAndDistributionInformation (archive-and-dist-info/parse-archive-dist-info doc
                                                                                         archive-info-xpath
                                                                                         dist-info-xpath)
       :DirectDistributionInformation (archive-and-dist-info/parse-direct-dist-info doc
                                                                                    dist-info-xpath)
       :MetadataSpecification (umm-c/map->MetadataSpecificationType
                             {:URL (str "https://cdn.earthdata.nasa.gov/umm/collection/v"
                                        umm-spec-versioning/current-collection-version),
                              :Name "UMM-C"
                              :Version umm-spec-versioning/current-collection-version})}))))
