(ns cmr.umm-spec.xml-to-umm-mappings.dif10
 "Defines mappings from DIF10 XML into UMM records"
 (:require
   [camel-snake-kebab.core :as csk]
   [clojure.string :as string]
   [cmr.common.date-time-parser :as dtp]
   [cmr.common.util :as util]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.umm-spec.date-util :as date]
   [cmr.umm-spec.dif-util :as dif-util]
   [cmr.umm-spec.json-schema :as js]
   [cmr.umm-spec.models.umm-collection-models :as umm-c]
   [cmr.umm-spec.url :as url]
   [cmr.umm-spec.util :as su :refer [without-default-value-of]]
   [cmr.umm-spec.xml-to-umm-mappings.characteristics-data-type-normalization :as char-data-type-normalization]
   [cmr.umm-spec.xml-to-umm-mappings.dif10.additional-attribute :as aa]
   [cmr.umm-spec.xml-to-umm-mappings.dif10.data-center :as center]
   [cmr.umm-spec.xml-to-umm-mappings.dif10.data-contact :as contact]
   [cmr.umm-spec.xml-to-umm-mappings.dif10.paleo-temporal :as pt]
   [cmr.umm-spec.xml-to-umm-mappings.dif10.related-url :as ru]
   [cmr.umm-spec.xml-to-umm-mappings.dif10.spatial :as spatial]
   [cmr.umm-spec.xml-to-umm-mappings.get-umm-element :as get-umm-element]
   [cmr.umm-spec.versioning :as umm-spec-versioning]
   [cmr.umm.dif.date-util :refer [parse-dif-end-date]]))

(def coll-progress-mapping
  "Mapping from values supported for DIF10 Data_Set_Progress to UMM CollectionProgress."
  {"COMPLETE" "COMPLETE"
   "IN WORK"  "ACTIVE"
   "PLANNED" "PLANNED"})

(defn- parse-characteristics
  [el]
  (seq (remove nil?
        (map char-data-type-normalization/normalize-data-type
         (remove nil?
          (for [characteristic (select el "Characteristics")]
            (fields-from characteristic :Name :Description :DataType :Unit :Value)))))))

(defn- parse-projects-impl
  [doc sanitize?]
  (for [proj (select doc "/DIF/Project")]
    {:ShortName (value-of proj "Short_Name")
     :LongName (su/truncate (value-of proj "Long_Name") su/PROJECT_LONGNAME_MAX sanitize?)
     :Campaigns (values-at proj "Campaign")
     :StartDate (date-at proj "Start_Date")
     :EndDate (date-at proj "End_Date")}))

(defn- parse-projects
  [doc sanitize?]
  (if sanitize?
    ;; We shouldn't remove not provided during parsing
    (when-not (= su/not-provided (value-of doc "/DIF/Project[1]/Short_Name"))
      (parse-projects-impl doc sanitize?))
    (parse-projects-impl doc sanitize?)))

(defn- parse-instruments-impl
  [platform-el]
  (for [inst (select platform-el "Instrument")]
    {:ShortName (value-of inst "Short_Name")
     :LongName (value-of inst "Long_Name")
     :Technique (value-of inst "Technique")
     :NumberOfInstruments (value-of inst "NumberOfSensors")
     :Characteristics (parse-characteristics inst)
     :OperationalModes (values-at inst "OperationalMode")
     :ComposedOf (for [sensor (select inst "Sensor")]
                  {:ShortName (value-of sensor "Short_Name")
                   :LongName (value-of sensor "Long_Name")
                   :Technique (value-of sensor "Technique")
                   :Characteristics (parse-characteristics sensor)})}))

(defn- parse-instruments
  [platform-el sanitize?]
  (if sanitize?
    ;; We shouldn't remove not provided during parsing
    (when-not (= su/not-provided (value-of platform-el "Instrument[1]/Short_Name"))
      (parse-instruments-impl platform-el))
    (parse-instruments-impl platform-el)))

(defn parse-standard-product
  "Returns UMM-C StandardProduct from DIF10 XML document.
  if multiple standard product values are present, pick the last one."
  [doc]
  (last (for [metadata (select doc "/DIF/Extended_Metadata/Metadata")
              :let [name (value-of metadata "Name")]
              :when (= name "StandardProduct")]
          (value-of metadata "Value"))))

(defn parse-data-dates
  "Returns seq of UMM-C DataDates parsed from DIF 10 XML document."
  [doc]
  (let [[md-dates-el] (select doc "/DIF/Metadata_Dates")
        tag-types [["Data_Creation"      "CREATE"]
                   ["Data_Last_Revision" "UPDATE"]
                   ["Data_Future_Review" "REVIEW"]
                   ["Data_Delete"        "DELETE"]]]
    (filter :Date
            (for [[tag date-type] tag-types
                  :let [date-value (-> md-dates-el
                                       (value-of tag)
                                       date/without-default
                                       (date/use-default-when-not-provided su/not-provided)
                                       ;; Since the DIF 10 date elements are actually just a string
                                       ;; type, they may contain anything, and so we need to try to
                                       ;; parse them here and return nil if they do not actually
                                       ;; represent dates.
                                       dtp/try-parse-datetime)]
                  :when date-value]
              {:Type date-type
               :Date date-value}))))

(defn- parse-metadata-dates
  "Returns a list of metadata dates"
  [doc]
  (seq (remove nil? [(date/parse-date-type-from-xml doc "DIF/Metadata_Dates/Metadata_Creation" "CREATE")
                     (date/parse-date-type-from-xml doc "DIF/Metadata_Dates/Metadata_Last_Revision" "UPDATE")
                     (date/parse-date-type-from-xml doc "DIF/Metadata_Dates/Metadata_Delete" "DELETE")
                     (date/parse-date-type-from-xml doc "DIF/Metadata_Dates/Metadata_Future_Review" "REVIEW")])))

(defn- parse-range-date-times
  "Returns a list of range date times"
  [temporal sanitize?]
  (let [rdts (for [rdt (select temporal "Range_DateTime")
                   :let [bdt (value-of rdt "Beginning_Date_Time")
                         edt (value-of rdt "Ending_Date_Time")
                         bdt-valid? (date/valid-date? bdt)
                         edt-valid? (date/valid-date? edt)]]
               (cond
                 (and bdt-valid?
                      edt-valid?)
                 {:BeginningDateTime (date/with-default (str (dtp/parse-datetime bdt)) sanitize?)
                  :EndingDateTime (parse-dif-end-date edt)}
                 (and bdt-valid?
                      (not edt-valid?))
                 {:BeginningDateTime (date/with-default (str (dtp/parse-datetime bdt)) sanitize?)}
                 (and (not bdt-valid?)
                      edt-valid?)
                 nil
                 (and (not bdt-valid?)
                      (not edt-valid?))
                 nil))
        rdts (remove nil? rdts)]
    (when (seq rdts) rdts)))

(defn- parse-temporal-extent
  "Return a temporal extent from a DIF10 Temporal_Coverage. Remove empty maps which could occur
  if only a Paleo Date Time is present."
  [temporal sanitize?]
  (let [temporal-extent
        (util/remove-map-keys empty?
                              {:PrecisionOfSeconds (value-of temporal "Precision_Of_Seconds")
                               :EndsAtPresentFlag (value-of temporal "Ends_At_Present_Flag")
                               :RangeDateTimes (parse-range-date-times temporal sanitize?)
                               :SingleDateTimes (dates-at-str temporal "Single_DateTime")
                               :PeriodicDateTimes (for [pdt (select temporal "Periodic_DateTime")]
                                                    {:Name (value-of pdt "Name")
                                                     :StartDate (date-at-str pdt "Start_Date")
                                                     :EndDate (parse-dif-end-date (value-of pdt "End_Date"))
                                                     :DurationUnit (value-of pdt "Duration_Unit")
                                                     :DurationValue (value-of pdt "Duration_Value")
                                                     :PeriodCycleDurationUnit (value-of pdt "Period_Cycle_Duration_Unit")
                                                     :PeriodCycleDurationValue (value-of pdt "Period_Cycle_Duration_Value")})})]
    (when (seq temporal-extent)
      ;; Do this after testing if the map is empty. The map will never be empty if we use the bool value
      (update temporal-extent :EndsAtPresentFlag #(Boolean/valueOf %)))))

(defn parse-temporal-extents
  "Returns a list of temportal extents"
  [doc sanitize?]
  (if-let [temporal-extents
           (seq (remove nil? (map #(parse-temporal-extent % sanitize?) (select doc "/DIF/Temporal_Coverage"))))]
    temporal-extents
    (when sanitize?
      su/not-provided-temporal-extents)))

(defn- remove-empty-collection-citations
  "Because DOI is mapped to Dataset Citations, we need to make sure on a xml round trip
   an empty CollectionCitation isn't left beind when the UMM starts with no CollectionCitations
   but does have a DOI"
  [collection-citations]
  (remove (fn [cc] (= {:OnlineResource {:Linkage "Not%20provided",
                                        :Name "Dataset Citation",
                                        :Description "Dataset Citation"}}
                      (util/remove-nil-keys cc)))
          collection-citations))

(defn- parse-collection-citation
  "Parse the Collection Citation from XML Data Set Citation"
  [doc sanitize?]
  (let [data-set-citations (seq (select doc "/DIF/Dataset_Citation"))]
    (remove-empty-collection-citations
     (for [data-set-citation data-set-citations
           :let [release-date (date/sanitize-and-parse-date (value-of data-set-citation "Dataset_Release_Date") sanitize?)]]
       {:Creator (value-of data-set-citation "Dataset_Creator")
        :Editor (value-of data-set-citation "Dataset_Editor")
        :Title  (value-of data-set-citation "Dataset_Title")
        :SeriesName (value-of data-set-citation "Dataset_Series_Name")
        :ReleaseDate (if sanitize?
                       (when (date/valid-date? release-date)
                         release-date)
                       release-date)
        :ReleasePlace (value-of data-set-citation "Dataset_Release_Place")
        :Publisher (value-of data-set-citation "Dataset_Publisher")
        :Version (value-of data-set-citation "Version")
        :IssueIdentification (value-of data-set-citation "Issue_Identification")
        :DataPresentationForm (value-of data-set-citation "Data_Presentation_Form")
        :OtherCitationDetails (value-of data-set-citation "Other_Citation_Details")
        :OnlineResource (when-let [linkage (value-of data-set-citation "Online_Resource")]
                          {:Linkage linkage})}))))

(defn- parse-archive-dist-info
  "Parses ArchiveAndDistributionInformation out of DIF XML into UMM-C"
  [doc]
  (let [distributions
        (for [distribution (select doc "/DIF/Distribution")
              :let [dist-size (value-of distribution "Distribution_Size")
                    {:keys [Size Unit]} (when dist-size
                                         (first (su/parse-data-sizes dist-size)))
                    media (value-of distribution "Distribution_Media")
                    media (when media
                            [media])
                    format (value-of distribution "Distribution_Format")
                    fees (value-of distribution "Fees")]]
          (when (or fees format Size Unit)
            {:Media media
             :AverageFileSize Size
             :AverageFileSizeUnit Unit
             :Format format
             :Fees fees
             :FormatType "Native"}))]
    (when (seq distributions)
      {:FileDistributionInformation distributions})))

(defn- parse-doi
  "Parse the DOI from the data citation section.  If the DOI does not exist then set the DOIs
   MissingReason and Explanation since it is required as of UMM-C 1.16.1."
  [doc]
  (let [first-doi
         (first (remove nil? (for [dsc (select doc "/DIF/Dataset_Citation")]
                               (when (= (value-of dsc "Persistent_Identifier/Type") "DOI")
                                 {:DOI (value-of dsc "Persistent_Identifier/Identifier")}))))]
    (if first-doi
      first-doi
      {:MissingReason "Unknown"
       :Explanation "It is unknown if this record has a DOI."})))

(defn- parse-associated-dois
  "Parse the associated DOIs."
  [doc]
  (if-let [assoc-dois (select doc "DIF/Associated_DOIs")]
    (into []
      (for [assoc-doi assoc-dois]
        {:DOI (value-of assoc-doi "DOI")
         :Title (value-of assoc-doi "Title")
         :Authority (value-of assoc-doi "Authority")}))))

(defn parse-use-constraints
  "Parse the XML collection Use Constraints into the UMM-C counterparts."
  [doc sanitize?]
  (when-let [use-constraints (first (select doc "DIF/Use_Constraints"))]
    (if (= java.lang.String (-> use-constraints
                               (get :content)
                               first
                               type))
      (when-let [description (su/truncate
                               (value-of use-constraints ".")
                               su/USECONSTRAINTS_MAX
                               sanitize?)]
        {:Description description})
      (util/remove-nils-empty-maps-seqs
        {:Description (value-of use-constraints "Description")
         :FreeAndOpenData (when-let [free-and-open (value-of use-constraints "Free_And_Open_Data")]
                            (Boolean/valueOf free-and-open))
         :LicenseURL (when-let [url (value-of use-constraints "License_URL/URL")]
                       {:Linkage url
                        :Name (value-of use-constraints "License_URL/Title")
                        :Description (value-of use-constraints "License_URL/Description")
                        :MimeType (value-of use-constraints "License_URL/Mime_Type")})
         :LicenseText (value-of use-constraints "License_Text")}))))

(defn parse-dif10-xml
  "Returns collection map from DIF10 collection XML document."
  [doc {:keys [sanitize?]}]
  {:EntryTitle (value-of doc "/DIF/Entry_Title")
   :DOI (parse-doi doc)
   :AssociatedDOIs (parse-associated-dois doc)
   :ShortName (value-of doc "/DIF/Entry_ID/Short_Name")
   :Version (value-of doc "/DIF/Entry_ID/Version")
   :VersionDescription (value-of doc "/DIF/Version_Description")
   :Abstract (su/truncate-with-default (value-of doc "/DIF/Summary/Abstract") su/ABSTRACT_MAX sanitize?)
   :CollectionDataType (value-of doc "/DIF/Collection_Data_Type")
   :CollectionCitations (parse-collection-citation doc sanitize?)
   :Purpose (su/truncate (value-of doc "/DIF/Summary/Purpose") su/PURPOSE_MAX sanitize?)
   :DataLanguage (dif-util/dif-language->umm-language (value-of doc "/DIF/Dataset_Language"))
   :DataDates (parse-data-dates doc)
   :MetadataDates (parse-metadata-dates doc)
   :ISOTopicCategories (dif-util/parse-iso-topic-categories doc)
   :TemporalKeywords (values-at doc "/DIF/Temporal_Coverage/Temporal_Info/Ancillary_Temporal_Keyword")
   :CollectionProgress (get-umm-element/get-collection-progress
                         coll-progress-mapping
                         doc
                         "/DIF/Dataset_Progress"
                         sanitize?)
   :LocationKeywords (for [lk (select doc "/DIF/Location")]
                       {:Category (value-of lk "Location_Category")
                        :Type (value-of lk "Location_Type")
                        :Subregion1 (value-of lk "Location_Subregion1")
                        :Subregion2 (value-of lk "Location_Subregion2")
                        :Subregion3 (value-of lk "Location_Subregion3")
                        :DetailedLocation (value-of lk "Detailed_Location")})
   :Projects (parse-projects doc sanitize?)
   :DirectoryNames (dif-util/parse-idn-node doc)
   :Quality (su/truncate (value-of doc "/DIF/Quality") su/QUALITY_MAX sanitize?)
   :AccessConstraints (dif-util/parse-access-constraints doc sanitize?)
   :UseConstraints (parse-use-constraints doc sanitize?)
   :Platforms (for [platform (select doc "/DIF/Platform")]
                {:ShortName (value-of platform "Short_Name")
                 :LongName (value-of platform "Long_Name")
                 :Type (without-default-value-of platform "Type")
                 :Characteristics (parse-characteristics platform)
                 :Instruments (parse-instruments platform sanitize?)})
   :TemporalExtents (parse-temporal-extents doc sanitize?)
   :PaleoTemporalCoverages (pt/parse-paleo-temporal doc)
   :SpatialExtent (spatial/parse-spatial doc)
   :TilingIdentificationSystems (spatial/parse-tiling doc)
   :ProcessingLevel {:Id (su/with-default (value-of doc "/DIF/Product_Level_Id") sanitize?)}
   :AdditionalAttributes (aa/xml-elem->AdditionalAttributes doc sanitize?)
   :StandardProduct (parse-standard-product doc)
   :PublicationReferences (for [pub-ref (select doc "/DIF/Reference")]
                            (into {} (map (fn [x]
                                            (if (keyword? x)
                                              [(csk/->PascalCaseKeyword x) (value-of pub-ref (str x))]
                                              x))
                                          [:Author
                                           [:PublicationDate (date/sanitize-and-parse-date (value-of pub-ref "Publication_Date") sanitize?)]
                                           :Title
                                           :Series
                                           :Edition
                                           :Volume
                                           :Issue
                                           :Report_Number
                                           :Publication_Place
                                           :Publisher
                                           :Pages
                                           [:ISBN (su/format-isbn (value-of pub-ref "ISBN"))]
                                           (when (= (value-of pub-ref "Persistent_Identifier/Type") "DOI")
                                             [:DOI {:DOI (value-of pub-ref "Persistent_Identifier/Identifier")}])
                                           [:OnlineResource (dif-util/parse-publication-reference-online-resouce pub-ref sanitize?)]
                                           :Other_Reference_Details])))
   :AncillaryKeywords (values-at doc "/DIF/Ancillary_Keyword")
   :RelatedUrls (ru/parse-related-urls doc sanitize?)
   :MetadataAssociations (for [ma (select doc "/DIF/Metadata_Association")]
                           {:EntryId (value-of ma "Entry_ID/Short_Name")
                            :Version (without-default-value-of ma "Entry_ID/Version")
                            :Description (without-default-value-of ma "Description")
                            :Type (string/upper-case (without-default-value-of ma "Type"))})
   :ScienceKeywords (for [sk (select doc "/DIF/Science_Keywords")]
                      {:Category (value-of sk "Category")
                       :Topic (value-of sk "Topic")
                       :Term (value-of sk "Term")
                       :VariableLevel1 (value-of sk "Variable_Level_1")
                       :VariableLevel2 (value-of sk "Variable_Level_2")
                       :VariableLevel3 (value-of sk "Variable_Level_3")
                       :DetailedVariable (value-of sk "Detailed_Variable")})
   :DataCenters (center/parse-data-centers doc sanitize?)
   :ContactPersons (contact/parse-contact-persons (select doc "/DIF/Personnel") sanitize?)
   :ContactGroups (contact/parse-contact-groups (select doc "DIF/Personnel"))
   :ArchiveAndDistributionInformation (parse-archive-dist-info doc)
   :DirectDistributionInformation (when-let [ddi (first
                                                   (select doc "/DIF/DirectDistributionInformation"))]
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

(defn dif10-xml-to-umm-c
  "Returns UMM-C collection record from DIF10 collection XML document. The :sanitize? option
  tells the parsing code to set the default values for fields when parsing the metadata into umm."
  [metadata options]
  (js/parse-umm-c (parse-dif10-xml metadata options)))
