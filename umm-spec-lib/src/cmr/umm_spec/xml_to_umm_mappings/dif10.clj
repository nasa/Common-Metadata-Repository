(ns cmr.umm-spec.xml-to-umm-mappings.dif10
  "Defines mappings from DIF10 XML into UMM records"
  (:require
    [camel-snake-kebab.core :as csk]
    [clojure.string :as string]
    [cmr.common.date-time-parser :as dtp]
    [cmr.common.xml.parse :refer :all]
    [cmr.common.xml.simple-xpath :refer [select]]
    [cmr.common.util :as util]
    [cmr.umm.dif.date-util :refer [parse-dif-end-date]]
    [cmr.umm-spec.date-util :as date]
    [cmr.umm-spec.dif-util :as dif-util]
    [cmr.umm-spec.json-schema :as js]
    [cmr.umm-spec.xml-to-umm-mappings.dif10.additional-attribute :as aa]
    [cmr.umm-spec.xml-to-umm-mappings.dif10.data-center :as center]
    [cmr.umm-spec.xml-to-umm-mappings.dif10.data-contact :as contact]
    [cmr.umm-spec.xml-to-umm-mappings.dif10.paleo-temporal :as pt]
    [cmr.umm-spec.xml-to-umm-mappings.dif10.related-url :as ru]
    [cmr.umm-spec.xml-to-umm-mappings.dif10.spatial :as spatial]
    [cmr.umm-spec.util :as u :refer [without-default-value-of]]))

(defn- parse-characteristics
  [el]
  (for [characteristic (select el "Characteristics")]
    (fields-from characteristic :Name :Description :DataType :Unit :Value)))

(defn- parse-projects-impl
  [doc sanitize?]
  (for [proj (select doc "/DIF/Project")]
    {:ShortName (value-of proj "Short_Name")
     :LongName (u/truncate (value-of proj "Long_Name") u/PROJECT_LONGNAME_MAX sanitize?)
     :Campaigns (values-at proj "Campaign")
     :StartDate (date-at proj "Start_Date")
     :EndDate (date-at proj "End_Date")}))

(defn- parse-projects
  [doc sanitize?]
  (if sanitize?
    ;; We shouldn't remove not provided during parsing
    (when-not (= u/not-provided (value-of doc "/DIF/Project[1]/Short_Name"))
      (parse-projects-impl doc sanitize?))
    (parse-projects-impl doc sanitize?)))

(defn- parse-instruments-impl
  [platform-el]
  (for [inst (select platform-el "Instrument")]
    {:ShortName (value-of inst "Short_Name")
     :LongName (value-of inst "Long_Name")
     :Technique (value-of inst "Technique")
     :NumberOfSensors (value-of inst "NumberOfSensors")
     :Characteristics (parse-characteristics inst)
     :OperationalModes (values-at inst "OperationalMode")
     :Sensors (for [sensor (select inst "Sensor")]
                {:ShortName (value-of sensor "Short_Name")
                 :LongName (value-of sensor "Long_Name")
                 :Technique (value-of sensor "Technique")
                 :Characteristics (parse-characteristics sensor)})}))

(defn- parse-instruments
  [platform-el sanitize?]
  (if sanitize?
    ;; We shouldn't remove not provided during parsing
    (when-not (= u/not-provided (value-of platform-el "Instrument[1]/Short_Name"))
      (parse-instruments-impl platform-el))
    (parse-instruments-impl platform-el)))

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
                                       (date/use-default-when-not-provided u/not-provided)
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
                     (date/parse-date-type-from-xml doc "DIF/Metadata_Dates/Metadata_Last_Revision" "UPDATE")])))

(defn- parse-temporal-extent
  "Return a temporal extent from a DIF10 Temporal_Coverage. Remove empty maps which could occur
  if only a Paleo Date Time is present."
  [temporal sanitize?]
  (let [temporal-extent
        (util/remove-map-keys empty?
                              {:TemporalRangeType (value-of temporal "Temporal_Range_Type")
                               :PrecisionOfSeconds (value-of temporal "Precision_Of_Seconds")
                               :EndsAtPresentFlag (value-of temporal "Ends_At_Present_Flag")
                               :RangeDateTimes (for [rdt (select temporal "Range_DateTime")]
                                                 {:BeginningDateTime (date/with-default (value-of rdt "Beginning_Date_Time") sanitize?)
                                                  :EndingDateTime (parse-dif-end-date (value-of rdt "Ending_Date_Time"))})
                               :SingleDateTimes (values-at temporal "Single_DateTime")
                               :PeriodicDateTimes (for [pdt (select temporal "Periodic_DateTime")]
                                                    {:Name (value-of pdt "Name")
                                                     :StartDate (value-of pdt "Start_Date")
                                                     :EndDate (parse-dif-end-date (value-of pdt "End_Date"))
                                                     :DurationUnit (value-of pdt "Duration_Unit")
                                                     :DurationValue (value-of pdt "Duration_Value")
                                                     :PeriodCycleDurationUnit (value-of pdt "Period_Cycle_Duration_Unit")
                                                     :PeriodCycleDurationValue (value-of pdt "Period_Cycle_Duration_Value")})})]
    (when (seq temporal-extent)
      temporal-extent)))

(defn- parse-temporal-extents
  "Returns a list of temportal extents"
  [doc sanitize?]
  (if-let [temporal-extents
           (seq (remove nil? (map #(parse-temporal-extent % sanitize?) (select doc "/DIF/Temporal_Coverage"))))]
    temporal-extents
    (when sanitize?
      u/not-provided-temporal-extents)))

(defn parse-dif10-xml
  "Returns collection map from DIF10 collection XML document."
  [doc {:keys [sanitize?]}]
  {:EntryTitle (value-of doc "/DIF/Entry_Title")
   :ShortName (value-of doc "/DIF/Entry_ID/Short_Name")
   :Version (value-of doc "/DIF/Entry_ID/Version")
   :Abstract (u/truncate-with-default (value-of doc "/DIF/Summary/Abstract") u/ABSTRACT_MAX sanitize?)
   :CollectionDataType (value-of doc "/DIF/Collection_Data_Type")
   :Purpose (u/truncate (value-of doc "/DIF/Summary/Purpose") u/PURPOSE_MAX sanitize?)
   :DataLanguage (dif-util/dif-language->umm-langage (value-of doc "/DIF/Dataset_Language"))
   :DataDates (parse-data-dates doc)
   :MetadataDates (parse-metadata-dates doc)
   :ISOTopicCategories (values-at doc "DIF/ISO_Topic_Category")
   :TemporalKeywords (values-at doc "/DIF/Temporal_Coverage/Temporal_Info/Ancillary_Temporal_Keyword")
   :CollectionProgress (value-of doc "/DIF/Dataset_Progress")
   :LocationKeywords (for [lk (select doc "/DIF/Location")]
                       {:Category (value-of lk "Location_Category")
                        :Type (value-of lk "Location_Type")
                        :Subregion1 (value-of lk "Location_Subregion1")
                        :Subregion2 (value-of lk "Location_Subregion2")
                        :Subregion3 (value-of lk "Location_Subregion3")
                        :DetailedLocation (value-of lk "Detailed_Location")})
   :Projects (parse-projects doc sanitize?)
   :Quality (u/truncate (value-of doc "/DIF/Quality") u/QUALITY_MAX sanitize?)
   :AccessConstraints (dif-util/parse-access-constraints doc sanitize?)
   :UseConstraints (u/truncate (value-of doc "/DIF/Use_Constraints") u/USECONSTRAINTS_MAX sanitize?)
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
   :Distributions (for [dist (select doc "/DIF/Distribution")]
                    {:DistributionMedia (value-of dist "Distribution_Media")
                     :Sizes (u/parse-data-sizes (value-of dist "Distribution_Size") sanitize?)
                     :DistributionFormat (value-of dist "Distribution_Format")
                     :Fees (value-of dist "Fees")})
   :ProcessingLevel {:Id (u/with-default (value-of doc "/DIF/Product_Level_Id") sanitize?)}
   :AdditionalAttributes (aa/xml-elem->AdditionalAttributes doc sanitize?)
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
                                           [:ISBN (u/format-isbn (value-of pub-ref "ISBN"))]
                                           (when (= (value-of pub-ref "Persistent_Identifier/Type") "DOI")
                                             [:DOI {:DOI (value-of pub-ref "Persistent_Identifier/Identifier")}])
                                           [:RelatedUrl
                                            {:URLs (seq
                                                     (remove nil? [(value-of pub-ref "Online_Resource")]))}]
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
   :ContactGroups (contact/parse-contact-groups (select doc "DIF/Personnel"))})

(defn dif10-xml-to-umm-c
  "Returns UMM-C collection record from DIF10 collection XML document. The :sanitize? option
  tells the parsing code to set the default values for fields when parsing the metadata into umm."
  [metadata options]
  (js/parse-umm-c (parse-dif10-xml metadata options)))
