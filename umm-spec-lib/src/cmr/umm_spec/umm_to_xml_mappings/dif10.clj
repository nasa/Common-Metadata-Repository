(ns cmr.umm-spec.umm-to-xml-mappings.dif10
  "Defines mappings from a UMM record into DIF10 XML"
  (:require
    [camel-snake-kebab.core :as csk]
    [clj-time.format :as f]
    [clojure.set :as set]
    [clojure.string :as string]
    [cmr.common.util :as util]
    [cmr.common.xml.gen :as gen]
    [cmr.umm-spec.date-util :as date]
    [cmr.umm-spec.dif-util :as dif-util]
    [cmr.umm-spec.umm-to-xml-mappings.dif10.data-center :as center]
    [cmr.umm-spec.umm-to-xml-mappings.dif10.data-contact :as contact]
    [cmr.umm-spec.umm-to-xml-mappings.dif10.spatial :as spatial]
    [cmr.umm-spec.util :as u :refer [with-default]]))

(def coll-progress-mapping
  "Mapping from known collection progress values to values supported for DIF10 Dataset_Progress."
  {"COMPLETE" "COMPLETE"
   "ACTIVE" "IN WORK"
   "PLANNED" "PLANNED"
   "DEPRECATED" "COMPLETE"})

(def platform-types
  "The set of values that DIF 10 defines for platform types as enumerations in its schema"
  #{u/not-provided
    "Aircraft"
    "Balloons/Rockets"
    "Earth Observation Satellites"
    "In Situ Land-based Platforms"
    "In Situ Ocean-based Platforms"
    "Interplanetary Spacecraft"
    "Maps/Charts/Photographs"
    "Models/Analyses"
    "Navigation Platforms"
    "Solar/Space Observation Satellites"
    "Space Stations/Manned Spacecraft"})

(def product-levels
  "The set of values that DIF 10 defines for Processing levels as enumerations in its schema"
  #{u/not-provided "0" "1" "1A" "1B" "1T" "2" "2G" "2P" "3" "4" "NA"})

(def dif10-xml-namespaces
  {:xmlns "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/"
   :xmlns:dif "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/"
   :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
   :xsi:schemaLocation "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/ http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/dif_v10.2.xsd"})

(defn- temporal-coverage-without-temporal-keywords
  "Returns the temporal coverage content without the temporal keywords"
  [extent]
  [:Temporal_Coverage
   [:Precision_Of_Seconds (:PrecisionOfSeconds extent)]
   [:Ends_At_Present_Flag (:EndsAtPresentFlag extent)]

   (for [rdt (:RangeDateTimes extent)]
     [:Range_DateTime
      [:Beginning_Date_Time (:BeginningDateTime rdt)]
      [:Ending_Date_Time (:EndingDateTime rdt)]])

   (for [sdt (:SingleDateTimes extent)]
     [:Single_DateTime sdt])

   (for [pdt (:PeriodicDateTimes extent)]
     [:Periodic_DateTime
      (gen/elements-from pdt :Name)
      [:Start_Date (:StartDate pdt)]
      [:End_Date (:EndDate pdt)]
      [:Duration_Unit (:DurationUnit pdt)]
      [:Duration_Value (:DurationValue pdt)]
      [:Period_Cycle_Duration_Unit (:PeriodCycleDurationUnit pdt)]
      [:Period_Cycle_Duration_Value (:PeriodCycleDurationValue pdt)]])])

(defn- generate-paleo-temporal
  "Returns the given paleo temporal in DIF10 format."
  [paleo-temporal]
  (when (seq paleo-temporal)
    [:Temporal_Coverage
     (for [{:keys [StartDate EndDate ChronostratigraphicUnits]} paleo-temporal]
       [:Paleo_DateTime
        [:Paleo_Start_Date StartDate]
        [:Paleo_Stop_Date EndDate]
        (for [{:keys [Eon Era Period Epoch Stage DetailedClassification]} ChronostratigraphicUnits]
          [:Chronostratigraphic_Unit
           [:Eon Eon]
           [:Era Era]
           [:Period Period]
           [:Epoch Epoch]
           [:Stage Stage]
           [:Detailed_Classification DetailedClassification]])])]))

(defn characteristics-for
  [obj]
  (for [characteristic (:Characteristics obj)]
    [:Characteristics
     (gen/elements-from characteristic
                    :Name
                    :Description
                    :DataType
                    :Unit
                    :Value)]))

(defn sensor-mapping
  [sensor]
  [:Sensor
   [:Short_Name (:ShortName sensor)]
   [:Long_Name (:LongName sensor)]
   [:Technique (:Technique sensor)]
   (characteristics-for sensor)])

(defn- generate-projects
  "Returns the content generator instructions for generating DIF10 projects. DIF10 projects
  is required, so we generate a dummy one when it is nil."
  [projects]
  (if (seq projects)
    (for [{:keys [ShortName LongName Campaigns StartDate EndDate]} projects]
      [:Project
       [:Short_Name ShortName]
       [:Campaign (first Campaigns)]
       [:Long_Name LongName]
       [:Start_Date (when StartDate (f/unparse (f/formatters :date) StartDate))]
       [:End_Date (when EndDate (f/unparse (f/formatters :date) EndDate))]])
    [:Project
     [:Short_Name u/not-provided]]))

(defn- generate-instruments
  "Returns the content generator instructions for generating DIF10 instruments. DIF10 instruments is
  a required field in PlatformType, so we generate a dummy one when it is nil."
  [instruments]
  (if (seq instruments)
    (for [instrument instruments]
      [:Instrument
       [:Short_Name (:ShortName instrument)]
       [:Long_Name (:LongName instrument)]
       [:Technique (:Technique instrument)]
       [:NumberOfSensors (:NumberOfInstruments instrument)]
       (characteristics-for instrument)
       (for [opmode (:OperationalModes instrument)]
         [:OperationalMode opmode])
       (map sensor-mapping (:ComposedOf instrument))])
    [:Instrument
     [:Short_Name u/not-provided]]))

(defn- generate-additional-attributes
  "Returns the content generator instructions for generating DIF10 additional attributes."
  [additional-attributes]
  (for [aa additional-attributes]
    [:Additional_Attributes
     [:Name (:Name aa)]
     [:DataType (:DataType aa)]
     [:Description (:Description aa)]
     [:MeasurementResolution (:MeasurementResolution aa)]
     [:ParameterRangeBegin (:ParameterRangeBegin aa)]
     [:ParameterRangeEnd (:ParameterRangeEnd aa)]
     [:ParameterUnitsOfMeasure (:ParameterUnitsOfMeasure aa)]
     [:ParameterValueAccuracy (:ParameterValueAccuracy aa)]
     [:ValueAccuracyExplanation (:ValueAccuracyExplanation aa)]
     [:Value (:Value aa)]]))

(defn- generate-data-dates
  "Returns DIF 10 elements for UMM-C collection c's DataDates."
  [c]
  (list
    [:Data_Creation (date/with-default (date/data-create-date c))]
    [:Data_Last_Revision (date/with-default (date/data-update-date c))]
    [:Data_Future_Review (date/data-review-date c)]
    [:Data_Delete (date/data-delete-date c)]))

(defn- generate-metadata-dates
  "Returns DIF 10 elements for UMM-C collection c's MetadataDates. Creation and Last Revision
  are required in DIF10 so use defaults if not present"
  [c]
  (remove nil? (list
                [:Metadata_Creation (f/unparse (f/formatters :date)
                                               (date/with-default-date (date/metadata-create-date c)))]
                [:Metadata_Last_Revision (f/unparse (f/formatters :date)
                                                    (date/with-default-date (date/metadata-update-date c)))]
                (when (date/metadata-review-date c)
                  [:Metadata_Future_Review (f/unparse (f/formatters :date)
                                                      (date/metadata-review-date c))])
                (when (date/metadata-delete-date c)
                  [:Metadata_Delete (f/unparse (f/formatters :date)
                                               (date/metadata-delete-date c))]))))

(defn- generate-related-urls
  "Returns DIF10 Related_URLs for the provided UMM-C collection record. Even though UMM RelatedUrls
  can come from DIF10 Related_URLs or Multimedia_Sample elements, we write out only to Related_URLs."
  [c]
  (if-let [urls (:RelatedUrls c)]
    (for [related-url urls
          :let [[type subtype] (dif-util/umm-url-type->dif-umm-content-type
                                (util/remove-nil-keys
                                 (select-keys related-url [:URLContentType :Type :Subtype])))
                mime-type (or (get-in related-url [:GetService :MimeType])
                              (get-in related-url [:GetData :MimeType]))
                protocol (get-in related-url [:GetService :Protocol])]]
      [:Related_URL
       [:URL_Content_Type
        [:Type type]
        [:Subtype subtype]]
       [:Protocol protocol]
       [:URL (get related-url :URL u/not-provided-url)]
       [:Description (:Description related-url)]
       [:Mime_Type mime-type]])
    ;; Default Related URL to add if none exist
    [:Related_URL
     [:URL u/not-provided-url]]))

(defn- generate-dataset-progress
  "Return Dataset_Progress attribute by translating from the UMM CollectionProgress to one of the
  DIF10 enumerations. Defaults to generating a Dataset_Progress of IN WORK if translation cannot be
  determined."
  [c]
  (when-let [c-progress (when-let [coll-progress (:CollectionProgress c)]
                          (get coll-progress-mapping (string/upper-case coll-progress)))]
    [:Dataset_Progress c-progress]))

(defn- dif10-product-level-id
  "Returns the given product-level-id in DIF10 format."
  [product-level-id]
  (when product-level-id
    (product-levels (string/replace product-level-id #"Level " ""))))

(defn generate-dataset-citation
  "Returns the dif10 Data_Set_Citations from UMM-C."
  [c]
  (let [doi (get-in c [:DOI :DOI])]
    (if (empty? (:CollectionCitations c))
      (when (seq doi)
        [:Dataset_Citation
         [:Persistent_Identifier
          [:Type "DOI"]
          [:Identifier doi]]])
      (for [collection-citation (:CollectionCitations c)]
        [:Dataset_Citation
         [:Dataset_Creator (:Creator collection-citation)]
         [:Dataset_Editor (:Editor collection-citation)]
         [:Dataset_Title (:Title collection-citation)]
         [:Dataset_Series_Name (:SeriesName collection-citation)]
         [:Dataset_Release_Date (:ReleaseDate collection-citation)]
         [:Dataset_Release_Place (:ReleasePlace collection-citation)]
         [:Dataset_Publisher (:Publisher collection-citation)]
         [:Version (:Version collection-citation)]
         [:Issue_Identification (:IssueIdentification collection-citation)]
         [:Data_Presentation_Form (:DataPresentationForm collection-citation)]
         [:Other_Citation_Details (:OtherCitationDetails collection-citation)]
         (when (seq doi)
           [:Persistent_Identifier
            [:Type "DOI"]
            [:Identifier doi]])
         (when-let [online-resource (:OnlineResource collection-citation)]
           [:Online_Resource (:Linkage online-resource)])]))))

(defn generate-associated-dois
  "Returns the DIF 10 XML associated dois from a UMM-C collection record."
  [c]
  (for [assoc-doi (get c :AssociatedDOIs)]
    [:Associated_DOIs
     [:DOI (:DOI assoc-doi)]
     [:Title (:Title assoc-doi)]
     [:Authority (:Authority assoc-doi)]]))

(defn umm-c-to-dif10-xml
  "Returns DIF10 XML from a UMM-C collection record."
  [c]
  (gen/xml
   [:DIF
    dif10-xml-namespaces
    [:Entry_ID
     [:Short_Name (:ShortName c)]
     [:Version (:Version c)]]
    [:Version_Description (:VersionDescription c)]
    [:Entry_Title (or (:EntryTitle c) u/not-provided)]
    (generate-dataset-citation c)
    (generate-associated-dois c)
    (contact/generate-collection-personnel c)
    (if-let [sks (:ScienceKeywords c)]
      ;; From UMM keywords
      (for [sk sks]
        [:Science_Keywords
         [:Category (:Category sk)]
         [:Topic (:Topic sk)]
         [:Term (:Term sk)]
         [:Variable_Level_1 (:VariableLevel1 sk)]
         [:Variable_Level_2 (:VariableLevel2 sk)]
         [:Variable_Level_3 (:VariableLevel3 sk)]
         [:Detailed_Variable (:DetailedVariable sk)]])
      ;; Default element
      [:Science_Keywords
       [:Category u/not-provided]
       [:Topic u/not-provided]
       [:Term u/not-provided]])

    (for [topic-category (:ISOTopicCategories c)]
      [:ISO_Topic_Category topic-category])

    (for [ak (:AncillaryKeywords c)]
      [:Ancillary_Keyword ak])

    (if-let [platforms (:Platforms c)]
      (for [platform platforms]
        [:Platform
         [:Type (get platform-types (:Type platform) u/not-provided)]
         [:Short_Name (:ShortName platform)]
         [:Long_Name (:LongName platform)]
         (characteristics-for platform)
         (generate-instruments (:Instruments platform))])
      ;; Default Platform element
      [:Platform
       [:Type u/not-provided]
       [:Short_Name u/not-provided]
       [:Long_Name u/not-provided]
       [:Instrument [:Short_Name u/not-provided]]])

    ;; DIF10 has TemporalKeywords bundled together with TemporalExtents in the Temporal_Coverage
    ;; element. There is no clear definition on which TemporalExtent the TemporalKeywords should
    ;; be associated with. This is something DIF10 team will look into at improving, but in the
    ;; mean time, we put the TemporalKeywords on the first TemporalExtent element.
    (let [extent (-> c :TemporalExtents first)]
      (conj (temporal-coverage-without-temporal-keywords extent)
            [:Temporal_Info
             (for [tkw (:TemporalKeywords c)]
               [:Ancillary_Temporal_Keyword tkw])]))

    (map temporal-coverage-without-temporal-keywords (drop 1 (:TemporalExtents c)))
    (generate-paleo-temporal (:PaleoTemporalCoverages c))
    (generate-dataset-progress c)
    (spatial/spatial-element c)
    (for [location-keyword-map (:LocationKeywords c)]
      [:Location
       [:Location_Category (:Category location-keyword-map)]
       [:Location_Type (:Type location-keyword-map)]
       [:Location_Subregion1 (:Subregion1 location-keyword-map)]
       [:Location_Subregion2 (:Subregion2 location-keyword-map)]
       [:Location_Subregion3 (:Subregion3 location-keyword-map)]
       [:Detailed_Location (:DetailedLocation location-keyword-map)]])
    (generate-projects (:Projects c))
    [:Quality (:Quality c)]
    [:Access_Constraints (-> c :AccessConstraints :Description)]
    (when-let [use-constraints (get c :UseConstraints)]
      [:Use_Constraints
        [:Description (:Description use-constraints)]
        (when (some? (:FreeAndOpenData use-constraints))
          [:Free_And_Open_Data (Boolean/valueOf (:FreeAndOpenData use-constraints))])
        (when-let [url (get-in use-constraints [:LicenseURL :Linkage])]
          [:License_URL
            [:URL url]
            [:Title (get-in use-constraints [:LicenseURL :Name])]
            [:Description (get-in use-constraints [:LicenseURL :Description])]
            [:Mime_Type (get-in use-constraints [:LicenseURL :MimeType])]])
        (when-let [license-text (:LicenseText use-constraints)]
          [:License_Text license-text])])
    (dif-util/generate-dataset-language :Dataset_Language (:DataLanguage c))
    (center/generate-organizations c)
    (for [dist (get-in c [:ArchiveAndDistributionInformation :FileDistributionInformation])]
      [:Distribution
       [:Distribution_Media (first (:Media dist))]
       [:Distribution_Size (when (:AverageFileSize dist)
                             (str (:AverageFileSize dist) " "
                                  (:AverageFileSizeUnit dist)))]
       [:Distribution_Format (:Format dist)]
       [:Fees (:Fees dist)]])
    (when-let [direct-dist-info (:DirectDistributionInformation c)]
      [:DirectDistributionInformation
        [:Region (:Region direct-dist-info)]
        (for [prefix-name (:S3BucketAndObjectPrefixNames direct-dist-info)]
          [:S3BucketAndObjectPrefixName prefix-name])
        [:S3CredentialsAPIEndpoint (:S3CredentialsAPIEndpoint direct-dist-info)]
        [:S3CredentialsAPIDocumentationURL (:S3CredentialsAPIDocumentationURL direct-dist-info)]])
    (for [pub-ref (:PublicationReferences c)]
      [:Reference
       (map (fn [x] (if (keyword? x)
                      [x ((csk/->PascalCaseKeyword x) pub-ref)]
                      x))
            [:Author
             :Publication_Date
             :Title
             :Series
             :Edition
             :Volume
             :Issue
             :Report_Number
             :Publication_Place
             :Publisher
             :Pages
             [:ISBN (:ISBN pub-ref)]
             (when (get-in pub-ref [:DOI :DOI])
               [:Persistent_Identifier
                 [:Type "DOI"]
                 [:Identifier (get-in pub-ref [:DOI :DOI])]])
             [:Online_Resource (get-in pub-ref [:OnlineResource :Linkage])]
             :Other_Reference_Details])])
    [:Summary
     ;; DIF 10 requires a Summary element but none of the contents are required, so either one will
     ;; work fine.
     [:Abstract (u/with-default (:Abstract c))]
     [:Purpose (:Purpose c)]]
    (generate-related-urls c)
    (for [ma (:MetadataAssociations c)
          :when (contains? #{"SCIENCE ASSOCIATED" "DEPENDENT" "INPUT" "PARENT" "CHILD" "RELATED" nil} (:Type ma))]
      [:Metadata_Association
       [:Entry_ID
        [:Short_Name (:EntryId ma)]
        [:Version (u/with-default (:Version ma))]]
       [:Type (or (u/capitalize-words (:Type ma)) "Science Associated")]
       [:Description (:Description ma)]])
    (dif-util/generate-idn-nodes c)
    [:Metadata_Name "CEOS IDN DIF"]
    [:Metadata_Version "VERSION 10.2"]
    [:Metadata_Dates
     (generate-metadata-dates c)
     (generate-data-dates c)]
    (generate-additional-attributes (:AdditionalAttributes c))
    [:Product_Level_Id (dif10-product-level-id (-> c :ProcessingLevel :Id))]
    [:Collection_Data_Type (:CollectionDataType c)]
    (when-let [access-value (get-in c [:AccessConstraints :Value])]
      [:Extended_Metadata
       [:Metadata
        [:Name "Restriction"]
        [:Value access-value]]])
    (let [standard-product (:StandardProduct c)]
      (when (some? (:StandardProduct c))
        [:Extended_Metadata
         [:Metadata
          [:Group "gov.nasa.gsfc.gcmd.standardproduct"]
          [:Name "StandardProduct"]
          [:Value standard-product]]]))]))
