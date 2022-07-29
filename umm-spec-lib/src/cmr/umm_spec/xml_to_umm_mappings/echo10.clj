(ns cmr.umm-spec.xml-to-umm-mappings.echo10
  "Defines mappings from ECHO10 XML into UMM records"
  (:require
   [clojure.string :as string]
   [cmr.common-app.services.kms-fetcher :as kf]
   [cmr.common.util :as util]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select text]]
   [cmr.umm-spec.date-util :as date]
   [cmr.umm-spec.json-schema :as js]
   [cmr.umm-spec.location-keywords :as lk]
   [cmr.umm-spec.models.umm-collection-models :as umm-c]
   [cmr.umm-spec.spatial-conversion :as spatial-conversion]
   [cmr.umm-spec.util :as u]
   [cmr.umm-spec.xml-to-umm-mappings.characteristics-data-type-normalization :as char-data-type-normalization]
   [cmr.umm-spec.xml-to-umm-mappings.echo10.data-contact :as dc]
   [cmr.umm-spec.xml-to-umm-mappings.echo10.related-url :as ru]
   [cmr.umm-spec.xml-to-umm-mappings.echo10.spatial :as spatial]
   [cmr.umm-spec.xml-to-umm-mappings.get-umm-element :as get-umm-element]
   [cmr.umm-spec.versioning :as umm-spec-versioning])
  (:import
   (clojure.data.xml Element)))

(def coll-progress-mapping
  "Mapping from values supported for ECHO10 CollectionState to UMM CollectionProgress."
  {"COMPLETE" "COMPLETE"
   "COMPLETED" "COMPLETE"
   "IN WORK" "ACTIVE"
   "ACTIVE" "ACTIVE"
   "PLANNED" "PLANNED"
   "DEPRECATED" "DEPRECATED"
   "NOT APPLICABLE" "NOT APPLICABLE"})

(defn parse-temporal
  "Returns seq of UMM temporal extents from an ECHO10 XML document."
  [doc]
  (for [^Element temporal (select doc "/Collection/Temporal")
        :let [^String ends-at-present-flag (value-of temporal "EndsAtPresentFlag")]]
    {:PrecisionOfSeconds (value-of temporal "PrecisionOfSeconds")
     :EndsAtPresentFlag (Boolean/valueOf ends-at-present-flag)
     :RangeDateTimes (for [rdt (select temporal "RangeDateTime")]
                       (fields-from rdt :BeginningDateTime :EndingDateTime))
     :SingleDateTimes (values-at temporal "SingleDateTime")
     :PeriodicDateTimes (for [pdt (select temporal "PeriodicDateTime")]
                          (fields-from pdt :Name :StartDate :EndDate :DurationUnit :DurationValue
                                       :PeriodCycleDurationUnit :PeriodCycleDurationValue))}))

(defn parse-characteristic
  "Returns a UMM characteristic record from an ECHO10 Characteristic element."
  [element]
  (fields-from element :Name :Description :DataType :Unit :Value))

(defn parse-characteristics
  "Returns a seq of UMM characteristic records from the element's child Characteristics."
  [el]
  (let [elements (select el "Characteristics/Characteristic")
        parsed-characteristics (remove nil? (map parse-characteristic elements))]
    (seq (remove nil?
           (map char-data-type-normalization/normalize-data-type parsed-characteristics)))))

(defn parse-sensor
  "Returns a UMM Sensor record from an ECHO10 Sensor element."
  [sensor-element]
  (assoc (fields-from sensor-element :ShortName :LongName :Technique)
         :Characteristics (parse-characteristics sensor-element)))

(defn parse-instrument
  "Returns a UMM Instrument record from an ECHO10 Instrument element."
  [inst]
  (assoc (fields-from inst :ShortName :LongName :Technique)
         :NumberOfInstruments (value-of inst "NumberOfSensors")
         :OperationalModes (values-at inst "OperationModes/OperationMode")
         :Characteristics (parse-characteristics inst)
         :ComposedOf (map parse-sensor (select inst "Sensors/Sensor"))))

(defn parse-metadata-association
  "Returns a UMM MetadataAssocation from an ECHO10 CollectionAsscociation element."
  [element]
  (let [version-id (value-of element "VersionId")
        assoc-type (value-of element "CollectionType")]
    {:EntryId (value-of element "ShortName")
     :Version (u/without-default version-id)
     :Type (some-> (u/without-default assoc-type)
                   string/upper-case)
     :Description (value-of element "CollectionUse")}))

(defn parse-metadata-associations
  "Returns a seq of UMM MetadataAssocations from an ECHO10 document."
  [doc]
  (map parse-metadata-association
       (select doc "/Collection/CollectionAssociations/CollectionAssociation")))

(defn parse-data-dates
  "Returns UMM DataDates seq from ECHO 10 XML document."
  [doc]
  (for [[date-type xpath] [["CREATE" "InsertTime"]
                           ["UPDATE" "LastUpdate"]
                           ["DELETE" "DeleteTime"]]
        :let [date-val (value-of doc (str "/Collection/" xpath))]
        :when date-val]
    {:Type date-type
     :Date date-val}))

(defn parse-tiling
  "Returns a UMM TilingIdentificationSystem map from the given ECHO10 XML document."
  [doc]
  (let [tiling-id-systems
        (for [sys-el (select doc "/Collection/TwoDCoordinateSystems/TwoDCoordinateSystem")]
         {:TilingIdentificationSystemName (value-of sys-el "TwoDCoordinateSystemName")
          :Coordinate1 (fields-from (first (select sys-el "Coordinate1")) :MinimumValue :MaximumValue)
          :Coordinate2 (fields-from (first (select sys-el "Coordinate2")) :MinimumValue :MaximumValue)})]
    (filter
     #(spatial-conversion/tile-id-system-name-is-valid?
       (:TilingIdentificationSystemName %))
     tiling-id-systems)))

(defn- parse-platforms
  "Parses platforms from the ECHO10 collection document."
  [doc]
  (for [plat (select doc "/Collection/Platforms/Platform")]
    {:ShortName (value-of plat "ShortName")
     :LongName (value-of plat "LongName")
     :Type (u/without-default-value-of plat "Type")
     :Characteristics (parse-characteristics plat)
     :Instruments (map parse-instrument (select plat "Instruments/Instrument"))}))

(defn- parse-metadata-dates
  "ECHO10 only has a revision date (UPDATE), so get that if applicable"
  [doc]
  (when-let [revision-date (date/parse-date-type-from-xml doc "Collection/RevisionDate" "UPDATE")]
    [revision-date]))

(defn- parse-science-keywords
  "Parse ECHO10 science keywords or use default if applicable"
  [doc sanitize?]
  (if-let [science-keywords (seq (select doc "/Collection/ScienceKeywords/ScienceKeyword"))]
    (for [sk science-keywords]
      {:Category (value-of sk "CategoryKeyword")
       :Topic (value-of sk "TopicKeyword")
       :Term (value-of sk "TermKeyword")
       :VariableLevel1 (value-of sk "VariableLevel1Keyword/Value")
       :VariableLevel2 (value-of sk "VariableLevel1Keyword/VariableLevel2Keyword/Value")
       :VariableLevel3 (value-of sk "VariableLevel1Keyword/VariableLevel2Keyword/VariableLevel3Keyword")
       :DetailedVariable (value-of sk "DetailedVariableKeyword")})
    (when sanitize?
      u/not-provided-science-keywords)))

(defn parse-access-constraints
  "If both value and Description are nil, return nil.
  Otherwise, if Description is nil, assoc it with u/not-provided"
  [doc sanitize?]
  (let [value (value-of doc "/Collection/RestrictionFlag")
        access-constraints-record
        {:Description (u/truncate
                       (value-of doc "/Collection/RestrictionComment")
                       u/ACCESSCONSTRAINTS_DESCRIPTION_MAX
                       sanitize?)
         :Value (when value
                 (Double/parseDouble value))}]
    (when (seq (util/remove-nil-keys access-constraints-record))
      (update access-constraints-record :Description #(u/with-default % sanitize?)))))

(defn parse-use-constraints
  "Parse the XML collection Use Constraints into the UMM-C counterparts."
  [doc]
  (when-let [use-constraints (first (select doc "Collection/UseConstraints"))]
    {:Description (value-of use-constraints "Description")
     :FreeAndOpenData (when-let [free-and-open (value-of use-constraints "FreeAndOpenData")]
                        (Boolean/valueOf free-and-open))
     :LicenseURL (when-let [url (value-of use-constraints "LicenseURL/URL")]
                   {:Linkage url
                    :Name (value-of use-constraints "LicenseURL/Type")
                    :Description (value-of use-constraints "LicenseURL/Description")
                    :MimeType (value-of use-constraints "LicenseURL/MimeType")})
     :LicenseText (value-of use-constraints "LicenseText")}))

(defn- parse-collection-doi
  "Parse the XML collection DOI into the UMM-C counterparts.
   There could be multiple DOIs under Collection, just take the first one for now."
  [doc]
  (if-let [doi (first (select doc "Collection/DOI"))]
    (let [doi-value (value-of doi "DOI")
          authority (value-of doi "Authority")
          missing-reason (value-of doi "MissingReason")
          explanation (value-of doi "Explanation")]
      (if (or doi-value authority)
        {:DOI (when doi-value
                doi-value)
         :Authority (when authority
                      authority)}
        {:MissingReason (when missing-reason
                          missing-reason)
         :Explanation (when explanation
                        explanation)}))
    {:MissingReason "Unknown"
     :Explanation "It is unknown if this record has a DOI."}))

(defn- parse-associated-dois
  "Parse the XML associated DOIs into the UMM-C counterparts."
  [doc]
  (when-let [assoc-dois (select doc "Collection/AssociatedDOIs/AssociatedDOI")]
    (into []
      (for [assoc-doi assoc-dois]
        {:DOI (value-of assoc-doi "DOI")
         :Title (value-of assoc-doi "Title")
         :Authority (value-of assoc-doi "Authority")}))))

(defn add-data-format
  "This function fills in the FileDistributionInformation
   elements from an echo 10 record."
  [price format]
  (util/remove-nil-keys
    {:Fees price
     :Format (value-of format ".")
     :FormatType "Native"}))

(defn parse-and-set-archive-dist-info
  "Parses price and data formats out of Echo 10 XML into UMM-C. The price
   is the same for all formats.  There is no way to distinguish them in
   ECHO 10."
  [doc]
  (let [price (value-of doc "Collection/Price")
        formats (select doc "Collection/DataFormat")]
    (if (and price (not formats))
      [{:Fees price
        :Format u/not-provided
        :FormatType "Native"}]
      (map #(add-data-format price %) formats))))

(defn parse-archive-dist-info
  "Parses ArchiveAndDistributionInformation out of Echo 10 XML into UMM-C"
  [doc]
  (let [distribution (parse-and-set-archive-dist-info doc)]
    (when-not (empty? distribution)
      {:FileDistributionInformation distribution})))

(defn- parse-echo10-xml
  "Returns UMM-C collection structure from ECHO10 collection XML document."
  [context doc {:keys [sanitize?]}]
  {:EntryTitle (value-of doc "/Collection/DataSetId")
   :DOI (parse-collection-doi doc)
   :AssociatedDOIs (parse-associated-dois doc)
   :ShortName  (value-of doc "/Collection/ShortName")
   :Version    (value-of doc "/Collection/VersionId")
   :VersionDescription (value-of doc "/Collection/VersionDescription")
   :DataDates  (parse-data-dates doc)
   :MetadataDates (parse-metadata-dates doc)
   :Abstract   (u/truncate (value-of doc "/Collection/Description") u/ABSTRACT_MAX sanitize?)
   :CollectionDataType (value-of doc "/Collection/CollectionDataType")
   :StandardProduct (value-of doc "/Collection/StandardProduct") 
   :Purpose    (u/truncate (value-of doc "/Collection/SuggestedUsage") u/PURPOSE_MAX sanitize?)
   :CollectionProgress (get-umm-element/get-collection-progress
                         coll-progress-mapping
                         doc
                         "/Collection/CollectionState"
                         sanitize?)
   :AccessConstraints (parse-access-constraints doc sanitize?)
   :UseConstraints (parse-use-constraints doc)
   :TemporalKeywords (values-at doc "/Collection/TemporalKeywords/Keyword")
   :LocationKeywords (lk/spatial-keywords->location-keywords
                      (kf/get-kms-index context)
                      (values-at doc "/Collection/SpatialKeywords/Keyword"))
   :SpatialExtent    (spatial/parse-spatial doc sanitize?)
   :TemporalExtents  (or (seq (parse-temporal doc))
                         (when sanitize? u/not-provided-temporal-extents))
   :Platforms (or (seq (parse-platforms doc))
                  (when sanitize? u/not-provided-platforms))
   :ProcessingLevel {:Id (u/with-default (value-of doc "/Collection/ProcessingLevelId") sanitize?)
                     :ProcessingLevelDescription (value-of doc "/Collection/ProcessingLevelDescription")}
   :AdditionalAttributes (for [aa (select doc "/Collection/AdditionalAttributes/AdditionalAttribute")]
                           {:Name (value-of aa "Name")
                            :DataType (value-of aa "DataType")
                            :Description (u/with-default (value-of aa "Description") sanitize?)
                            :ParameterRangeBegin (value-of aa "ParameterRangeBegin")
                            :ParameterRangeEnd (value-of aa "ParameterRangeEnd")
                            :Value (value-of aa "Value")})
   :MetadataAssociations (parse-metadata-associations doc)
   :Projects (for [proj (select doc "/Collection/Campaigns/Campaign")]
               {:ShortName (value-of proj "ShortName")
                :LongName (u/truncate (value-of proj "LongName") u/PROJECT_LONGNAME_MAX sanitize?)
                :StartDate (value-of proj "StartDate")
                :EndDate (value-of proj "EndDate")})
   :TilingIdentificationSystems (parse-tiling doc)
   :RelatedUrls (ru/parse-related-urls doc sanitize?)
   :ScienceKeywords (parse-science-keywords doc sanitize?)
   :DataCenters (dc/parse-data-centers doc sanitize?)
   :ContactPersons (dc/parse-data-contact-persons doc sanitize?)
   :CollectionCitations (when-let [collection-citations (value-of doc "/Collection/CitationForExternalPublication")]
                          [{:OtherCitationDetails collection-citations}])
   :ArchiveAndDistributionInformation (parse-archive-dist-info doc)
   :DirectDistributionInformation (when-let [ddi (first
                                                   (select doc "/Collection/DirectDistributionInformation"))]
                                    {:Region (value-of ddi "Region")
                                     :S3BucketAndObjectPrefixNames
                                       (values-at ddi "S3BucketAndObjectPrefixName")
                                     :S3CredentialsAPIEndpoint
                                       (value-of ddi "S3CredentialsAPIEndpoint")
                                     :S3CredentialsAPIDocumentationURL
                                       (value-of ddi "S3CredentialsAPIDocumentationURL")})
   :MetadataSpecification (umm-c/map->MetadataSpecificationType
                             {:URL (str "https://cdn.earthdata.nasa.gov/umm/collection/v"
                                        umm-spec-versioning/current-collection-version),
                              :Name "UMM-C"
                              :Version umm-spec-versioning/current-collection-version})})

(defn echo10-xml-to-umm-c
  "Returns UMM-C collection record from ECHO10 collection XML document. The :sanitize? option
  tells the parsing code to set the default values for fields when parsing the metadata into umm."
  [context metadata options]
  (js/parse-umm-c (parse-echo10-xml context metadata options)))
