(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2
  "Defines mappings from ISO19115-2 XML to UMM records"
  (:require
   [clojure.data :as data]
   [clojure.string :as str]
   [cmr.common.util :as util]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select text]]
   [cmr.umm-spec.iso-keywords :as kws]
   [cmr.umm-spec.iso19115-2-util :as iso-util :refer [char-string-value]]
   [cmr.umm-spec.json-schema :as js]
   [cmr.umm-spec.location-keywords :as lk]
   [cmr.umm-spec.util :as su :refer [char-string]]
   [cmr.umm-spec.util :as u]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.additional-attribute :as aa]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.distributions-related-url :as dru]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.metadata-association :as ma]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.platform :as platform]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.spatial :as spatial]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.tiling-system :as tiling]))

(def md-data-id-base-xpath
  "/gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification")

(def citation-base-xpath
  (str md-data-id-base-xpath "/gmd:citation/gmd:CI_Citation"))

(def identifier-base-xpath
  (str citation-base-xpath "/gmd:identifier/gmd:MD_Identifier"))

(def constraints-xpath
  (str md-data-id-base-xpath "/gmd:resourceConstraints/gmd:MD_LegalConstraints"))

(def temporal-xpath
  "Temoral xpath relative to md-data-id-base-xpath"
  (str "gmd:extent/gmd:EX_Extent/gmd:temporalElement/gmd:EX_TemporalExtent/gmd:extent"))

(def data-quality-info-xpath
  "/gmi:MI_Metadata/gmd:dataQualityInfo/gmd:DQ_DataQuality")

(def quality-xpath
  (str data-quality-info-xpath
       "/gmd:report/DQ_QuantitativeAttributeAccuracy/gmd:evaluationMethodDescription"))

(def precision-xpath
  (str data-quality-info-xpath
       "/gmd:report/gmd:DQ_AccuracyOfATimeMeasurement/gmd:result/gmd:DQ_QuantitativeResult"
       "/gmd:value/gco:Record[@xsi:type='gco:Real_PropertyType']/gco:Real"))

(def topic-categories-xpath
  (str md-data-id-base-xpath "/gmd:topicCategory/gmd:MD_TopicCategoryCode"))

(def data-dates-xpath
  (str md-data-id-base-xpath "/gmd:citation/gmd:CI_Citation/gmd:date/gmd:CI_Date"))

(def projects-xpath
  (str "/gmi:MI_Metadata/gmi:acquisitionInformation/gmi:MI_AcquisitionInformation/gmi:operation"
       "/gmi:MI_Operation"))

(def publication-xpath
  "Publication xpath relative to md-data-id-base-xpath"
  (str "gmd:aggregationInfo/gmd:MD_AggregateInformation/gmd:aggregateDataSetName/gmd:CI_Citation"
       "[gmd:citedResponsibleParty/gmd:CI_ResponsibleParty/gmd:role/gmd:CI_RoleCode='publication']"))

(def personnel-xpath
  "/gmi:MI_Metadata/gmd:contact/gmd:CI_ResponsibleParty")

(defn- descriptive-keywords-type-not-equal
  "Returns the descriptive keyword values for the given parent element for all keyword types excepting
  those given"
  [md-data-id-el keyword-types-to-ignore]
  (let [keyword-types-to-ignore (set keyword-types-to-ignore)]
    (flatten
      (for [kw (select md-data-id-el "gmd:descriptiveKeywords/gmd:MD_Keywords")
            :when (not (keyword-types-to-ignore (value-of kw "gmd:type/gmd:MD_KeywordTypeCode")))]
        (values-at kw "gmd:keyword/gco:CharacterString")))))

(defn- regex-value
  "Utitlity function to return the value of the element that matches the given xpath and regex."
  [element xpath regex]
  (when-let [elements (select element xpath)]
    (when-let [matches (seq
                         (for [match-el elements
                               :let [match (re-matches regex (text match-el))]
                               :when match]
                           ;; A string response implies there is no group in the regular expression and the
                           ;; entire matching string is returned and if there is a group in the regular
                           ;; expression, the first group of the matching string is returned.
                           (if (string? match) match (second match))))]
      (str/join matches))))

(defn- parse-projects
  "Returns the projects parsed from the given xml document."
  [doc sanitize?]
  (for [proj (select doc projects-xpath)]
    (let [short-name (value-of proj iso-util/short-name-xpath)
          description (char-string-value proj "gmi:description")
          ;; ISO description is built as "short-name > long-name", so here we extract the long-name out
          long-name (when-not (= short-name description)
                      (str/replace description (str short-name iso-util/keyword-separator-join) ""))]
      {:ShortName short-name
       :LongName (u/truncate long-name u/PROJECT_LONGNAME_SIZE sanitize?)})))

(defn- temporal-ends-at-present?
  [temporal-el]
  (-> temporal-el
      (select "gml:TimePeriod/gml:endPosition[@indeterminatePosition='now']")
      seq
      some?))

(defn- parse-temporal-extents
  "Parses the collection temporal extents from the the collection document, the extent information,
  and the data identification element."
  [doc extent-info md-data-id-el]
  (for [temporal (select md-data-id-el temporal-xpath)]
    {:PrecisionOfSeconds (value-of doc precision-xpath)
     :EndsAtPresentFlag (temporal-ends-at-present? temporal)
     :TemporalRangeType (get extent-info "Temporal Range Type")
     :RangeDateTimes (for [period (select temporal "gml:TimePeriod")]
                       {:BeginningDateTime (value-of period "gml:beginPosition")
                        :EndingDateTime    (value-of period "gml:endPosition")})
     :SingleDateTimes (values-at temporal "gml:TimeInstant/gml:timePosition")}))

(defn- parse-access-constraints
  "If both value and Description are nil, return nil.
  Otherwise, if Description is nil, assoc it with u/not-provided"
  [doc sanitize?]
  (let [access-constraints-record
        {:Description (regex-value doc (str constraints-xpath
                                         "/gmd:useLimitation/gco:CharacterString")
                         #"(?s)Restriction Comment: (.+)")
         :Value (regex-value doc (str constraints-xpath
                                   "/gmd:otherConstraints/gco:CharacterString")
                   #"(?s)Restriction Flag:(.+)")}]
    (when (seq (util/remove-nil-keys access-constraints-record))
      (update access-constraints-record :Description #(u/with-default % sanitize?)))))

(defn- parse-iso19115-xml
  "Returns UMM-C collection structure from ISO19115-2 collection XML document."
  [context doc {:keys [sanitize?]}]
  (let [md-data-id-el (first (select doc md-data-id-base-xpath))
        citation-el (first (select doc citation-base-xpath))
        id-el (first (select doc identifier-base-xpath))
        extent-info (iso-util/get-extent-info-map doc)]
    {:ShortName (char-string-value id-el "gmd:code")
     :EntryTitle (char-string-value citation-el "gmd:title")
     :Version (char-string-value citation-el "gmd:edition")
     :Abstract (u/truncate-with-default (char-string-value md-data-id-el "gmd:abstract") u/ABSTRACT_SIZE sanitize?)
     :Purpose (u/truncate (char-string-value md-data-id-el "gmd:purpose") u/PURPOSE_SIZE sanitize?)
     :CollectionProgress (value-of md-data-id-el "gmd:status/gmd:MD_ProgressCode")
     :Quality (u/truncate (char-string-value doc quality-xpath) u/QUALITY_SIZE sanitize?)
     :DataDates (iso-util/parse-data-dates doc data-dates-xpath)
     :AccessConstraints (parse-access-constraints doc sanitize?)
     :UseConstraints
     (u/truncate
       (regex-value doc (str constraints-xpath "/gmd:useLimitation/gco:CharacterString")
                    #"(?s)^(?!Restriction Comment:).+")
       u/USECONSTRAINTS_SIZE
       sanitize?)
     :LocationKeywords (lk/translate-spatial-keywords
                         context (kws/descriptive-keywords md-data-id-el "place"))
     :TemporalKeywords (kws/descriptive-keywords md-data-id-el "temporal")
     :DataLanguage (char-string-value md-data-id-el "gmd:language")
     :ISOTopicCategories (values-at doc topic-categories-xpath)
     :SpatialExtent (spatial/parse-spatial doc extent-info sanitize?)
     :TilingIdentificationSystems (tiling/parse-tiling-system md-data-id-el)
     :TemporalExtents (or (seq (parse-temporal-extents doc extent-info md-data-id-el))
                          (when sanitize? u/not-provided-temporal-extents))
     :ProcessingLevel {:Id
                       (su/with-default
                         (char-string-value
                           md-data-id-el "gmd:processingLevel/gmd:MD_Identifier/gmd:code")
                         sanitize?)
                       :ProcessingLevelDescription
                       (char-string-value
                         md-data-id-el "gmd:processingLevel/gmd:MD_Identifier/gmd:description")}
     :Distributions (dru/parse-distributions doc sanitize?)
     :Platforms (platform/parse-platforms doc sanitize?)
     :Projects (parse-projects doc sanitize?)

     :PublicationReferences (for [publication (select md-data-id-el publication-xpath)
                                  :let [role-xpath "gmd:citedResponsibleParty/gmd:CI_ResponsibleParty[gmd:role/gmd:CI_RoleCode/@codeListValue='%s']"
                                        select-party (fn [name xpath]
                                                       (char-string-value publication
                                                                          (str (format role-xpath name) xpath)))]]
                              {:Author (select-party "author" "/gmd:organisationName")
                               :PublicationDate (str (date-at publication
                                                              (str "gmd:date/gmd:CI_Date[gmd:dateType/gmd:CI_DateTypeCode/@codeListValue='publication']/"
                                                                   "gmd:date/gco:Date")))
                               :Title (char-string-value publication "gmd:title")
                               :Series (char-string-value publication "gmd:series/gmd:CI_Series/gmd:name")
                               :Edition (char-string-value publication "gmd:edition")
                               :Issue (char-string-value publication "gmd:series/gmd:CI_Series/gmd:issueIdentification")
                               :Pages (char-string-value publication "gmd:series/gmd:CI_Series/gmd:page")
                               :Publisher (select-party "publisher" "/gmd:organisationName")
                               :ISBN (su/format-isbn (char-string-value publication "gmd:ISBN"))
                               :DOI {:DOI (char-string-value publication "gmd:identifier/gmd:MD_Identifier/gmd:code")}
                               :OtherReferenceDetails (char-string-value publication "gmd:otherCitationDetails")})
     :MetadataAssociations (ma/xml-elem->metadata-associations doc)
     :AncillaryKeywords (descriptive-keywords-type-not-equal
                          md-data-id-el
                          ["place" "temporal" "project" "platform" "instrument" "theme"])
     :ScienceKeywords (kws/parse-science-keywords md-data-id-el sanitize?)
     :RelatedUrls (dru/parse-related-urls doc sanitize?)
     :AdditionalAttributes (aa/parse-additional-attributes doc sanitize?)
     ;; DataCenters is not implemented but is required in UMM-C
     ;; Implement with CMR-3161
     :DataCenters (when sanitize? [u/not-provided-data-center])}))

(defn iso19115-2-xml-to-umm-c
  "Returns UMM-C collection record from ISO19115-2 collection XML document. The :sanitize? option
  tells the parsing code to set the default values for fields when parsing the metadata into umm."
  [context metadata options]
  (js/parse-umm-c (parse-iso19115-xml context metadata options)))
