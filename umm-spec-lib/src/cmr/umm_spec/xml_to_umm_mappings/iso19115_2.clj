(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2
  "Defines mappings from ISO19115-2 XML to UMM records"
  (:require
   [clj-time.format :as f]
   [clojure.string :as string]
   [cmr.common.util :as util]
   [cmr.common.xml.parse :refer [date-at date-at-str value-of values-at]]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.umm-spec.date-util :as date]
   [cmr.umm-spec.iso-keywords :as kws]
   [cmr.umm-spec.iso19115-2-util :as iso-util :refer [char-string-value gmx-anchor-value]]
   [cmr.umm-spec.json-schema :as js]
   [cmr.umm-spec.models.umm-collection-models :as umm-c]
   [cmr.umm-spec.url :as url]
   [cmr.umm-spec.util :as su]
   [cmr.umm-spec.xml-to-umm-mappings.get-umm-element :as get-umm-element]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.archive-and-dist-info :as archive-and-dist-info]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.collection-citation :as collection-citation]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.doi :as doi]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.iso-topic-categories :as iso-topic-categories]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.platform :as platform]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.project-element :as project]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.shared-iso-parsing-util :as parsing-util]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.use-constraints :as use-constraints]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.additional-attribute :as aa]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.data-contact :as data-contact]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.distributions-related-url :as dru]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.metadata-association :as ma]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.spatial :as spatial]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.tiling-system :as tiling]
   [cmr.umm-spec.versioning :as umm-spec-versioning]))

(def coll-progress-mapping
  "Mapping from values supported for ISO19115 ProgressCode to UMM CollectionProgress."
  {"COMPLETED" "COMPLETE"
   "HISTORICALARCHIVE" "DEPRECATED"
   "OBSOLETE" "DEPRECATED"
   "RETIRED" "DEPRECATED"
   "DEPRECATED" "DEPRECATED"
   "ONGOING" "ACTIVE"
   "PLANNED" "PLANNED"
   "UNDERDEVELOPMENT" "PLANNED"
   "INREVIEW" "INREVIEW"
   "SUPERSEDED" "SUPERSEDED"
   "PREPRINT" "PREPRINT"
   "NOT APPLICABLE" "NOT PROVIDED"})

(def md-data-id-base-xpath
  "/gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification")

(def citation-base-xpath
  (str md-data-id-base-xpath "/gmd:citation/gmd:CI_Citation"))

(def identifier-base-xpath
  (str citation-base-xpath "/gmd:identifier/gmd:MD_Identifier"))

(def constraints-xpath
  (str md-data-id-base-xpath "/gmd:resourceConstraints/gmd:MD_LegalConstraints"))

(def temporal-xpath
  (str md-data-id-base-xpath "/gmd:extent/gmd:EX_Extent/gmd:temporalElement"))

(def data-quality-info-xpath
  "/gmi:MI_Metadata/gmd:dataQualityInfo/gmd:DQ_DataQuality")

(def quality-xpath
  (str data-quality-info-xpath
       "/gmd:report/DQ_QuantitativeAttributeAccuracy/gmd:evaluationMethodDescription"))

(def precision-xpath
  (str "gmd:DQ_AccuracyOfATimeMeasurement/gmd:result/gmd:DQ_QuantitativeResult"
       "/gmd:value/gco:Record[@xsi:type='gco:Real_PropertyType']/gco:Real"))

(def data-dates-xpath
  (str md-data-id-base-xpath "/gmd:citation/gmd:CI_Citation/gmd:date/gmd:CI_Date"))

(def projects-xpath
  (str "/gmi:MI_Metadata/gmi:acquisitionInformation/gmi:MI_AcquisitionInformation/gmi:operation"
       "/gmi:MI_Operation"))

(def publication-xpath
  "Publication xpath relative to md-data-id-base-xpath"
  (str "gmd:aggregationInfo/gmd:MD_AggregateInformation/gmd:aggregateDataSetName/gmd:CI_Citation"
       "[gmd:citedResponsibleParty/gmd:CI_ResponsibleParty/gmd:role/gmd:CI_RoleCode='publication']"))

(def metadata-extended-element-xpath
  (str "/gmi:MI_Metadata/gmd:metadataExtensionInfo/gmd:MD_MetadataExtensionInformation"
       "/gmd:extendedElementInformation/gmd:MD_ExtendedElementInformation"))

(def collection-data-type-xpath
  (str identifier-base-xpath
       "[gmd:codeSpace/gco:CharacterString='gov.nasa.esdis.umm.collectiondatatype']"
       "/gmd:code/gco:CharacterString"))

(def standard-product-xpath
  (str identifier-base-xpath
       "[gmd:codeSpace/gco:CharacterString='gov.nasa.esdis.umm.standardproduct']"
       "/gmd:code/gco:CharacterString"))

(def platform-alternative-xpath
  (str "/gmi:MI_Metadata/gmi:acquisitionInformation/gmi:MI_AcquisitionInformation/gmi:platform"
       "/gmi:MI_Platform"))

(def instrument-alternative-xpath
  "NOAA instrument xpath"
  (str "/gmi:MI_Metadata/gmi:acquisitionInformation/gmi:MI_AcquisitionInformation/gmi:instrument"
       "/gmi:MI_Instrument"))

(def archive-info-xpath
  "/gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:resourceFormat")

(def dist-info-xpath
  "/gmi:MI_Metadata/gmd:distributionInfo/gmd:MD_Distribution")

(def associated-doi-xpath
  (str md-data-id-base-xpath "/gmd:aggregationInfo/gmd:MD_AggregateInformation/"))

(defn- descriptive-keywords-type-not-equal
  "Returns the descriptive keyword values for the given parent element for all keyword types excepting
  those given"
  [md-data-id-el keyword-types-to-ignore]
  (let [keyword-types-to-ignore (set keyword-types-to-ignore)]
    (flatten
      (for [kw (select md-data-id-el "gmd:descriptiveKeywords/gmd:MD_Keywords")
            :when (not (keyword-types-to-ignore (value-of kw "gmd:type/gmd:MD_KeywordTypeCode")))]
        (values-at kw "gmd:keyword/gco:CharacterString")))))

(defn temporal-ends-at-present?
  [temporal-el]
  (->> temporal-el
       (some #(select % "gmd:EX_TemporalExtent/gmd:extent/gml:TimePeriod/gml:endPosition[@indeterminatePosition='now']"))
       some?))

(defn get-attribute
  "Given an element, return the value of the attribute off of it.
  This function assumes there is only 1 attribute on the element."
  [element]
  (-> element      ;; list of 1 element that contains needed attribute.
       (first)     ;; takes element out of list.
       (:attrs)    ;; #:gml{:id temporal_extent_2_resolution}
       (first)     ;; [:gml/id temporal_extent_2_resolution]
       (second)))  ;; temporal_extent_2_resolution

(defn find-temporal-resolution-non-value-unit
  "Find the non value temporal resolution unit if it exists which
  is either the enumerations of varies or constant."
  [temporal-extent resolution-key]
  (let [element (select temporal-extent "gmd:EX_TemporalExtent/gmd:extent/gml:TimeInstant")
        attribute-value (get-attribute element)]
    (when (= resolution-key attribute-value)
      (let [value (value-of element "/")]
        (when value
          (if (string/includes? (string/lower-case value) "constant")
            "Constant"
            "Varies"))))))

(defn find-temporal-resolution-value-unit
  "Find the temporal resolution value and unit if it exists."
  [temporal-extent]
  (let [value (value-of temporal-extent "gmd:EX_TemporalExtent/gmd:extent/gml:TimePeriod/gml:timeInterval")
        value (when value
                (util/str->num (string/trim value)))
        unit (value-of (first (select temporal-extent "gmd:EX_TemporalExtent/gmd:extent/gml:TimePeriod/gml:timeInterval")) "@unit")
        unit (when unit
               (string/capitalize unit))]
    (when value
      {:Value value
       :Unit unit})))

(defn parse-temporal-resolution
  "Returns the temporal resolution map given a list of extents with the same group uuidref."
  [temporal-extents-list temporal-group-key]
  (let [non-value-unit (some #(find-temporal-resolution-non-value-unit % (str temporal-group-key "_resolution")) temporal-extents-list)
        value-unit (some #(find-temporal-resolution-value-unit %) temporal-extents-list)]
    (if non-value-unit
      {:Unit non-value-unit}
      value-unit)))

(defn find-single-date-time
  "Returns the single date time if it exists in the ISO temporal extents sub elements."
  [temporal-extent group-key]
  (let [element (select temporal-extent "gmd:EX_TemporalExtent/gmd:extent/gml:TimeInstant")
        attribute-value (get-attribute element)]
    (when (and element
               (or (nil? group-key)
                   (= group-key attribute-value)))
      (date-at-str (first element) "gml:timePosition"))))

(defn parse-temporal-extents
  "Parses the collection temporal extents from the the collection document
  and the data identification element."
  [doc]
  (seq
   (for [temporal-group (group-by #(value-of % "@uuidref") (select doc temporal-xpath))
         :let [temporal-extents-list (val temporal-group)]]
     {:PrecisionOfSeconds (if (key temporal-group)
                            (or (value-of doc (str data-quality-info-xpath
                                                   "/gmd:report[@uuidref='"
                                                   (key temporal-group)
                                                   "']/"
                                                   precision-xpath))
                                (value-of doc (str data-quality-info-xpath
                                                   "/gmd:report/"
                                                   precision-xpath)))
                            (value-of doc (str data-quality-info-xpath
                                               "/gmd:report/"
                                               precision-xpath)))
      :EndsAtPresentFlag (temporal-ends-at-present? temporal-extents-list)
      :RangeDateTimes (seq (util/remove-nils-empty-maps-seqs
                            (for [period temporal-extents-list
                                  :let [time-period (first (select period "gmd:EX_TemporalExtent/gmd:extent/gml:TimePeriod"))]
                                  :when time-period]
                              {:BeginningDateTime (date-at-str time-period "gml:beginPosition")
                               :EndingDateTime    (date-at-str time-period "gml:endPosition")})))
      :SingleDateTimes (seq
                        (util/remove-nils-empty-maps-seqs (map #(find-single-date-time % (key temporal-group)) temporal-extents-list)))
      :TemporalResolution (parse-temporal-resolution temporal-extents-list (key temporal-group))})))

(defn- parse-abstract-version-description
  "Returns the Abstract and VersionDescription parsed from the collection
  DataIdentification element"
  [md-data-id-el sanitize?]
  (if-let [value (char-string-value md-data-id-el "gmd:abstract")]
    (let [[abstract version-description] (string/split
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
    (when-let [linkage (value-of online-resource "gmd:linkage/gmd:URL")]
      {:Linkage (url/format-url linkage sanitize?)
       :Protocol (char-string-value online-resource "gmd:protocol")
       :ApplicationProfile (char-string-value online-resource "gmd:applicationProfile")
       :Name (char-string-value online-resource ":gmd:name")
       :Description (char-string-value online-resource "gmd:description")
       :Function (value-of online-resource "gmd:function/gmd:CI_OnLineFunctionCode")}))))

(defn- parse-doi-for-publication-reference
  "Returns the DOI field within a publication reference."
  [pub-ref]
  (when-let [doi-value (char-string-value pub-ref "gmd:identifier/gmd:MD_Identifier/gmd:code")]
    {:DOI doi-value}))

(defn- parse-publication-references
  "Returns the publication references."
  [md-data-id-el sanitize?]
  (for [publication (select md-data-id-el publication-xpath)
        :let [role-xpath "gmd:citedResponsibleParty/gmd:CI_ResponsibleParty[gmd:role/gmd:CI_RoleCode/@codeListValue='%s']"
              online-resource (parse-online-resource publication sanitize?)
              select-party (fn [name xpath]
                             (char-string-value publication
                                                (str (format role-xpath name) xpath)))]
         :when (or (nil? (:Description online-resource))
                   (not (string/includes? (:Description online-resource) "PublicationURL")))]
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
     :DOI (parse-doi-for-publication-reference publication)
     :OnlineResource (parse-online-resource publication sanitize?)
     :OtherReferenceDetails (char-string-value publication "gmd:otherCitationDetails")}))

(defn parse-other-identifiers
  "Return the UMM-C OtherIdentifiers from an ISO record."
  [doc]
  (for [id-el (select doc identifier-base-xpath)
        :let [id (char-string-value id-el "gmd:code")
              desc (char-string-value id-el "gmd:description")
              desc-map (when desc
                         (parsing-util/convert-iso-description-string-to-map
                          desc
                          (re-pattern "Type:|DescriptionOfOtherType:")))]
        :when (= "gov.nasa.esdis.umm.otheridentifier" (value-of id-el "gmd:codeSpace/gco:CharacterString"))]
    (if (:DescriptionOfOtherType desc-map)
      {:Identifier id
       :Type (:Type desc-map)
       :DescriptionOfOtherType (:DescriptionOfOtherType desc-map)}
      {:Identifier id
       :Type (:Type desc-map)})))

(defn parse-file-naming-convention
  "Return the UMM-C FileNamingConvention from an ISO record."
  [doc]
  (first
   (for [format (select doc (str archive-info-xpath "/gmd:MD_Format"))
         :let [format-name (char-string-value format "gmd:name")
               specification (char-string-value format "gmd:specification")
               spec-map (when specification
                          (parsing-util/convert-iso-description-string-to-map
                           specification
                           (re-pattern "FileNameConvention:|ConventionDescription:")))]
         :when (= "FileNamingConvention" format-name)]
     {:Convention (:FileNameConvention spec-map)
      :Description (:ConventionDescription spec-map)})))

(def data-maturity-valid-values
  "Vector list of the data maturity valid values"
  ["Beta" "Provisional" "Validated" "Stage 1 Validation" "Stage 2 Validation" "Stage 3 Validation" "Stage 4 Validation"])

(defn parse-data-maturity
  "Return the UMM-C DataMaturity element and the sub elements from an ISO record."
  [doc]
  (first
   (for [id-el (select doc identifier-base-xpath)
         :let [id (char-string-value id-el "gmd:code")]
         :when (= "gov.nasa.esdis.umm.datamaturity" (value-of id-el "gmd:codeSpace/gco:CharacterString"))]
     (when (and id (some #(= id %) data-maturity-valid-values))
       id))))

(defn- parse-iso19115-xml
  "Returns UMM-C collection structure from ISO19115-2 collection XML document."
  [doc {:keys [sanitize?]}]
  (let [md-data-id-el (first (select doc md-data-id-base-xpath))
        citation-el (first (select doc citation-base-xpath))
        id-el (first (select doc identifier-base-xpath))
        alt-xpath-options {:plat-alt-xpath platform-alternative-xpath
                           :inst-alt-xpath instrument-alternative-xpath}
        [abstract version-description] (parse-abstract-version-description md-data-id-el sanitize?)]
    (merge
     (data-contact/parse-contacts doc sanitize?) ; DataCenters, ContactPersons, ContactGroups
     {:ShortName (or (char-string-value id-el "gmd:code")
                     (gmx-anchor-value id-el "gmd:code"))
      :EntryTitle (char-string-value citation-el "gmd:title")
      :DOI (doi/parse-doi doc citation-base-xpath)
      :OtherIdentifiers (parse-other-identifiers doc)
      :FileNamingConvention (parse-file-naming-convention doc)
      :AssociatedDOIs (doi/parse-associated-dois doc associated-doi-xpath)
      :Version (or (char-string-value citation-el "gmd:edition") "Not Applicable")
      :VersionDescription version-description
      :Abstract abstract
      :Purpose (su/truncate (char-string-value md-data-id-el "gmd:purpose") su/PURPOSE_MAX sanitize?)
      :CollectionProgress (get-umm-element/get-collection-progress
                           coll-progress-mapping
                           md-data-id-el
                           "gmd:status/gmd:MD_ProgressCode"
                           sanitize?)
      :DataMaturity (parse-data-maturity doc)
      :Quality (su/truncate (char-string-value doc quality-xpath) su/QUALITY_MAX sanitize?)
      :DataDates (iso-util/parse-data-dates doc data-dates-xpath)
      :AccessConstraints (use-constraints/parse-access-constraints doc constraints-xpath sanitize?)
      :UseConstraints (use-constraints/parse-use-constraints doc constraints-xpath sanitize?)
      :LocationKeywords (kws/parse-location-keywords md-data-id-el)
      :TemporalKeywords (kws/descriptive-keywords md-data-id-el "temporal")
      :DataLanguage (char-string-value md-data-id-el "gmd:language")
      :ISOTopicCategories (iso-topic-categories/parse-iso-topic-categories doc "")
      :SpatialExtent (spatial/parse-spatial doc sanitize?)
      :TilingIdentificationSystems (tiling/parse-tiling-system md-data-id-el)
      :TemporalExtents (or (seq (parse-temporal-extents doc))
                           (when sanitize? su/not-provided-temporal-extents))
      :CollectionDataType (value-of (select doc collection-data-type-xpath) ".")
      :StandardProduct (value-of (select doc standard-product-xpath) ".")
      :ProcessingLevel {:Id
                        (su/with-default
                          (char-string-value
                           md-data-id-el "gmd:processingLevel/gmd:MD_Identifier/gmd:code")
                          sanitize?)
                        :ProcessingLevelDescription
                        (char-string-value
                         md-data-id-el "gmd:processingLevel/gmd:MD_Identifier/gmd:description")}
      :Platforms (platform/parse-platforms doc "" sanitize? alt-xpath-options)
      :Projects (project/parse-projects doc projects-xpath sanitize?)

      :PublicationReferences (parse-publication-references md-data-id-el sanitize?)
      :MetadataAssociations (ma/xml-elem->metadata-associations doc)
      :AncillaryKeywords (descriptive-keywords-type-not-equal
                          md-data-id-el
                          ["place" "temporal" "project" "platform" "instrument" "theme"])
      :ScienceKeywords (kws/parse-science-keywords md-data-id-el sanitize?)
      :RelatedUrls (dru/parse-related-urls doc sanitize?)
      :CollectionCitations (collection-citation/parse-collection-citation doc citation-base-xpath sanitize?)
      :AdditionalAttributes (aa/parse-additional-attributes doc sanitize?)
      :MetadataDates (parse-metadata-dates doc)
      :ArchiveAndDistributionInformation (archive-and-dist-info/parse-archive-dist-info doc
                                                                                        archive-info-xpath
                                                                                        dist-info-xpath)
      :DirectDistributionInformation (archive-and-dist-info/parse-direct-dist-info doc
                                                                                   dist-info-xpath)
      :MetadataSpecification (umm-c/map->MetadataSpecificationType
                              {:URL (str "https://cdn.earthdata.nasa.gov/umm/collection/v"
                                         umm-spec-versioning/current-collection-version),
                               :Name "UMM-C"
                               :Version umm-spec-versioning/current-collection-version})})))

(defn iso19115-2-xml-to-umm-c
  "Returns UMM-C collection record from ISO19115-2 collection XML document. The :sanitize? option
  tells the parsing code to set the default values for fields when parsing the metadata into umm."
  [_context metadata options]
  (js/parse-umm-c (parse-iso19115-xml metadata options)))
