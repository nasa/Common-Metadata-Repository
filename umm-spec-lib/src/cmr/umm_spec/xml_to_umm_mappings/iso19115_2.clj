(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2
  "Defines mappings from ISO19115-2 XML to UMM records"
  (:require
   [clj-time.format :as f]
   [clojure.data :as data]
   [clojure.string :as str]
   [cmr.common-app.services.kms-fetcher :as kf]
   [cmr.common.util :as util]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select text]]
   [cmr.umm-spec.date-util :as date]
   [cmr.umm-spec.iso-keywords :as kws]
   [cmr.umm-spec.iso19115-2-util :as iso-util :refer [char-string-value]]
   [cmr.umm-spec.json-schema :as js]
   [cmr.umm-spec.location-keywords :as lk]
   [cmr.umm-spec.url :as url]
   [cmr.umm-spec.util :as su :refer [char-string]]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.additional-attribute :as aa]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.data-contact :as data-contact]
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

(def metadata-extended-element-xpath
  (str "/gmi:MI_Metadata/gmd:metadataExtensionInfo/gmd:MD_MetadataExtensionInformation"
       "/gmd:extendedElementInformation/gmd:MD_ExtendedElementInformation"))

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
          long-name (value-of proj iso-util/long-name-xpath) 
          start-end-date (when-let [date (value-of proj "gmi:description/gco:CharacterString")]
                           (str/split (str/trim date) #"\s+"))
          ;; date is built as: StartDate: 2001:01:01T01:00:00Z EndDate: 2002:02:02T01:00:00Z
          ;; One date can exist without the other.
          start-date (when start-end-date 
                       (if (= "StartDate:" (get start-end-date 0))
                         (get start-end-date 1)
                         (get start-end-date 3)))
          end-date (when start-end-date
                     (if (= "EndDate:" (get start-end-date 0))
                       (get start-end-date 1)
                       (get start-end-date 3)))
          campaigns (seq (map #(value-of % iso-util/campaign-xpath) (select proj "gmi:childOperation")))]
      (util/remove-nil-keys
        {:ShortName short-name
         :LongName (su/truncate long-name su/PROJECT_LONGNAME_MAX sanitize?)
         :StartDate start-date
         :EndDate end-date
         :Campaigns campaigns}))))

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

(defn parse-access-constraints
  "If both value and Description are nil, return nil.
  Otherwise, if Description is nil, assoc it with su/not-provided"
  [doc sanitize?]
  (let [value (regex-value doc (str constraints-xpath
                                 "/gmd:otherConstraints/gco:CharacterString")
               #"(?s)Restriction Flag:(.+)")
        access-constraints-record
        {:Description (su/truncate
                       (regex-value doc (str constraints-xpath
                                         "/gmd:useLimitation/gco:CharacterString")
                         #"(?s)Restriction Comment: (.+)")
                       su/ACCESSCONSTRAINTS_DESCRIPTION_MAX
                       sanitize?)
         :Value (when value
                 (Double/parseDouble value))}]
    (when (seq (util/remove-nil-keys access-constraints-record))
      (update access-constraints-record :Description #(su/with-default % sanitize?)))))

(defn- parse-abstract-version-description
  "Returns the Abstract and VersionDescription parsed from the collection
  DataIdentification element"
  [md-data-id-el sanitize?]
  (if-let [value (char-string-value md-data-id-el "gmd:abstract")]
    (let [[abstract version-description](str/split
                                         value (re-pattern iso-util/version-description-separator))
          abstract (when (seq abstract) abstract)]
      [(su/truncate-with-default abstract su/ABSTRACT_MAX sanitize?) version-description])
    [(su/with-default nil sanitize?)]))

(defn- parse-metadata-dates
 "Parse the metadata dates from the ISO doc if they exist. The metadata dates come from the extended-metadata
 metadata in the ISO doc. Extended metadata isn't necessarily the metadata dates so check the definition
 to see if it's metadata dates and filter those out in the for."
 [doc]
 (for [metadata-date (select doc metadata-extended-element-xpath)
       :let [date-definition (char-string-value metadata-date "gmd:definition")
             metadata-date-type (get iso-util/umm-metadata-date-types date-definition)]
       :when (some? metadata-date-type)]
   {:Type metadata-date-type
    :Date (f/parse (char-string-value metadata-date "gmd:domainValue"))}))


(defn- parse-online-resource
 "Parse online resource from the publication XML"
 [publication sanitize?]
 (when-let [party
            (first (select publication (str "gmd:citedResponsibleParty/gmd:CI_ResponsibleParty"
                                            "[gmd:role/gmd:CI_RoleCode/@codeListValue='resourceProvider']")))]
  (when-let [online-resource
             (first (select party "gmd:contactInfo/gmd:CI_Contact/gmd:onlineResource/gmd:CI_OnlineResource"))]
    {:Linkage (url/format-url (value-of online-resource "gmd:linkage/gmd:URL") sanitize?)
     :Protocol (char-string-value online-resource "gmd:protocol")
     :ApplicationProfile (char-string-value online-resource "gmd:applicationProfile")
     :Name (su/with-default (char-string-value online-resource ":gmd:name") sanitize?)
     :Description (su/with-default (char-string-value online-resource "gmd:description") sanitize?)
     :Function (value-of online-resource "gmd:function/gmd:CI_OnLineFunctionCode")})))

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

(defn- parse-iso19115-xml
  "Returns UMM-C collection structure from ISO19115-2 collection XML document."
  [context doc {:keys [sanitize?]}]
  (let [md-data-id-el (first (select doc md-data-id-base-xpath))
        citation-el (first (select doc citation-base-xpath))
        id-el (first (select doc identifier-base-xpath))
        extent-info (iso-util/get-extent-info-map doc)
        [abstract version-description] (parse-abstract-version-description md-data-id-el sanitize?)]
    (merge
     (data-contact/parse-contacts doc sanitize?) ; DataCenters, ContactPersons, ContactGroups
     {:ShortName (char-string-value id-el "gmd:code")
      :EntryTitle (char-string-value citation-el "gmd:title")
      :DOI (parse-doi doc)
      :Version (char-string-value citation-el "gmd:edition")
      :VersionDescription version-description
      :Abstract abstract
      :Purpose (su/truncate (char-string-value md-data-id-el "gmd:purpose") su/PURPOSE_MAX sanitize?)
      :CollectionProgress (su/with-default (value-of md-data-id-el "gmd:status/gmd:MD_ProgressCode") sanitize?)
      :Quality (su/truncate (char-string-value doc quality-xpath) su/QUALITY_MAX sanitize?)
      :DataDates (iso-util/parse-data-dates doc data-dates-xpath)
      :AccessConstraints (parse-access-constraints doc sanitize?)
      :UseConstraints
      (su/truncate
       (regex-value doc (str constraints-xpath "/gmd:useLimitation/gco:CharacterString")
                    #"(?s)^(?!Restriction Comment:).+")
       su/USECONSTRAINTS_MAX
       sanitize?)
      :LocationKeywords (kws/parse-location-keywords md-data-id-el)
      :TemporalKeywords (kws/descriptive-keywords md-data-id-el "temporal")
      :DataLanguage (char-string-value md-data-id-el "gmd:language")
      :ISOTopicCategories (values-at doc topic-categories-xpath)
      :SpatialExtent (spatial/parse-spatial doc extent-info sanitize?)
      :TilingIdentificationSystems (tiling/parse-tiling-system md-data-id-el)
      :TemporalExtents (or (seq (parse-temporal-extents doc extent-info md-data-id-el))
                           (when sanitize? su/not-provided-temporal-extents))
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
                                         online-resource (parse-online-resource publication sanitize?)
                                         select-party (fn [name xpath]
                                                        (char-string-value publication
                                                                           (str (format role-xpath name) xpath)))]
                                    :when (or (nil? (:Description online-resource))
                                              (not (str/includes? (:Description online-resource) "PublicationURL")))]
                               {:Author (select-party "author" "/gmd:organisationName")
                                :PublicationDate (date/sanitize-and-parse-date
                                                  (str (date-at publication
                                                                (str "gmd:date/gmd:CI_Date[gmd:dateType/gmd:CI_DateTypeCode/@codeListValue='publication']/"
                                                                     "gmd:date/gco:Date")))
                                                  sanitize?)
                                :Title (char-string-value publication "gmd:title")
                                :Series (char-string-value publication "gmd:series/gmd:CI_Series/gmd:name")
                                :Edition (char-string-value publication "gmd:edition")
                                :Issue (char-string-value publication "gmd:series/gmd:CI_Series/gmd:issueIdentification")
                                :Pages (char-string-value publication "gmd:series/gmd:CI_Series/gmd:page")
                                :Publisher (select-party "publisher" "/gmd:organisationName")
                                :ISBN (su/format-isbn (char-string-value publication "gmd:ISBN"))
                                :DOI {:DOI (char-string-value publication "gmd:identifier/gmd:MD_Identifier/gmd:code")}
                                :OnlineResource (parse-online-resource publication sanitize?)
                                :OtherReferenceDetails (char-string-value publication "gmd:otherCitationDetails")})
      :MetadataAssociations (ma/xml-elem->metadata-associations doc)
      :AncillaryKeywords (descriptive-keywords-type-not-equal
                          md-data-id-el
                          ["place" "temporal" "project" "platform" "instrument" "theme"])
      :ScienceKeywords (kws/parse-science-keywords md-data-id-el sanitize?)
      :RelatedUrls (dru/parse-related-urls doc sanitize?)
      :AdditionalAttributes (aa/parse-additional-attributes doc sanitize?)
      :MetadataDates (parse-metadata-dates doc)})))

(defn iso19115-2-xml-to-umm-c
  "Returns UMM-C collection record from ISO19115-2 collection XML document. The :sanitize? option
  tells the parsing code to set the default values for fields when parsing the metadata into umm."
  [context metadata options]
  (js/parse-umm-c (parse-iso19115-xml context metadata options)))

(defn parse-doc-temporal-extents
 "Standalone function to parse temporal extents outside of full collection parsing"
 [doc]
 (let [md-data-id-el (first (select doc md-data-id-base-xpath))
       extent-info (iso-util/get-extent-info-map doc)]
  (parse-temporal-extents doc extent-info md-data-id-el)))
