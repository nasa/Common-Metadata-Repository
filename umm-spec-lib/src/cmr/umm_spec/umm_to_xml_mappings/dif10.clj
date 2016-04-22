(ns cmr.umm-spec.umm-to-xml-mappings.dif10
  "Defines mappings from a UMM record into DIF10 XML"
  (:require [cmr.common.xml.gen :refer :all]
            [cmr.umm-spec.umm-to-xml-mappings.dif10.spatial :as spatial]
            [cmr.umm-spec.date-util :as date]
            [camel-snake-kebab.core :as csk]
            [clj-time.format :as f]
            [cmr.umm-spec.util :as u :refer [with-default]]
            [clojure.string :as str]))

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
   [:Temporal_Range_Type (:TemporalRangeType extent)]
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
      (elements-from pdt :Name)
      [:Start_Date (:StartDate pdt)]
      [:End_Date (:EndDate pdt)]
      [:Duration_Unit (:DurationUnit pdt)]
      [:Duration_Value (:DurationValue pdt)]
      [:Period_Cycle_Duration_Unit (:PeriodCycleDurationUnit pdt)]
      [:Period_Cycle_Duration_Value (:PeriodCycleDurationValue pdt)]])])

(defn characteristics-for
  [obj]
  (for [characteristic (:Characteristics obj)]
    [:Characteristics
     (elements-from characteristic
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
       [:NumberOfSensors (:NumberOfSensors instrument)]
       (characteristics-for instrument)
       (for [opmode (:OperationalModes instrument)]
         [:OperationalMode opmode])
       (map sensor-mapping (:Sensors instrument))])
    [:Instrument
     [:Short_Name u/not-provided]]))

(defn- generate-additional-attributes
  "Returns the content generator instructions for generating DIF10 additional attributes."
  [additional-attributes]
  (for [aa additional-attributes]
    [:Additional_Attributes
     [:Name (:Name aa)]
     [:DataType (:DataType aa)]
     [:Description (with-default (:Description aa))]
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
    [:Data_Creation (date/or-default (date/data-create-date c))]
    [:Data_Last_Revision (date/or-default (date/data-update-date c))]
    [:Data_Future_Review (date/data-review-date c)]
    [:Data_Delete (date/data-delete-date c)]))

(defn- generate-related-urls
  "Returns DIF10 Related_URLs for the provided UMM-C collection record."
  [c]
  (if-let [urls (:RelatedUrls c)]
    (for [related-url urls]
      [:Related_URL
       (when-let [[type subtype] (:Relation related-url)]
         [:URL_Content_Type
          [:Type type]
          [:Subtype subtype]])
       ;; Adding a dummy URL if none exists since it is required
       (for [url (get related-url :URLs ["http://example.com"])]
         [:URL url])
       [:Description (:Description related-url)]])
    ;; Default Related URL to add if none exist
    [:Related_URL
     [:URL "http://example.com"]]))

(def iso-639-2->dif10-dataset-language
  "Mapping from ISO 639-2 to the enumeration supported for dataset languages in DIF10."
  {"eng" "English"
   "afr" "Afrikaans"
   "ara" "Arabic"
   "bos" "Bosnian"
   "bul" "Bulgarian"
   "chi" "Chinese"
   "zho" "Chinese"
   "hrv" "Croatian"
   "cze" "Czech"
   "ces" "Czech"
   "dan" "Danish"
   "dum" "Dutch"
   "dut" "Dutch"
   "nld" "Dutch"
   "est" "Estonian"
   "fin" "Finnish"
   "fre" "French"
   "fra" "French"
   "gem" "German"
   "ger" "German"
   "deu" "German"
   "gmh" "German"
   "goh" "German"
   "gsw" "German"
   "nds" "German"
   "heb" "Hebrew"
   "hun" "Hungarian"
   "ind" "Indonesian"
   "ita" "Italian"
   "jpn" "Japanese"
   "kor" "Korean"
   "lav" "Latvian"
   "lit" "Lithuanian"
   "nno" "Norwegian"
   "nob" "Norwegian"
   "nor" "Norwegian"
   "pol" "Polish"
   "por" "Portuguese"
   "rum" "Romanian"
   "ron" "Romanian"
   "rup" "Romanian"
   "rus" "Russian"
   "slo" "Slovak"
   "slk" "Slovak"
   "spa" "Spanish"
   "ukr" "Ukrainian"
   "vie" "Vietnamese"})

(def dif10-dataset-languages
  "Set of Dataset_Languages supported in DIF10"
  (set (vals iso-639-2->dif10-dataset-language)))

(defn- generate-metadata-language
  "Return Dataset_Language attribute by translating from the UMM DataLanguage to one of the DIF10
  enumerations. Defaults to generating a Dataset_Language of English if translation cannot be
  determined."
  [c]
  (when-let [data-language (:DataLanguage c)]
    [:Dataset_Language (if (dif10-dataset-languages data-language)
                         data-language
                         (get iso-639-2->dif10-dataset-language data-language "English"))]))

(def collection-progress->dif10-dataset-progress
  "Mapping from known collection progress values to values supported for DIF10 Dataset_Progress."
  {"PLANNED" "PLANNED"
   "ONGOING" "IN WORK"
   "ONLINE" "IN WORK"
   "COMPLETED" "COMPLETE"
   "FINAL" "COMPLETE"})

(def dif10-dataset-progress-values
  "Set of Dataset_Progress values supported in DIF10"
  (set (distinct (vals collection-progress->dif10-dataset-progress))))

(defn- generate-dataset-progress
  "Return Dataset_Progress attribute by translating from the UMM CollectionProgress to one of the
  DIF10 enumerations. Defaults to generating a Dataset_Progress of IN WORK if translation cannot be
  determined."
  [c]
  (when-let [c-progress (when-let [coll-progress (:CollectionProgress c)]
                          (str/upper-case coll-progress))]
    [:Dataset_Progress (if (dif10-dataset-progress-values c-progress)
                         c-progress
                         (get collection-progress->dif10-dataset-progress c-progress "IN WORK"))]))

(defn- dif10-product-level-id
  "Returns the given product-level-id in DIF10 format."
  [product-level-id]
  (when product-level-id
    (product-levels (str/replace product-level-id #"Level " ""))))

(defn umm-c-to-dif10-xml
  "Returns DIF10 XML from a UMM-C collection record."
  [c]
  (xml
    [:DIF
     dif10-xml-namespaces
     [:Entry_ID
      [:Short_Name (:ShortName c)]
      [:Version (u/with-default (:Version c))]]
     [:Entry_Title (or (:EntryTitle c) u/not-provided)]

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
     (if-let [extent (-> c :TemporalExtents first)]
       (conj (temporal-coverage-without-temporal-keywords extent)
             [:Temporal_Info
              (for [tkw (:TemporalKeywords c)]
                [:Ancillary_Temporal_Keyword tkw])])

       ;; default Temporal_Coverage element
       [:Temporal_Coverage
        [:Range_DateTime
         [:Beginning_Date_Time date/default-date-value]]])

     (map temporal-coverage-without-temporal-keywords (drop 1 (:TemporalExtents c)))
     (generate-dataset-progress c)
     (spatial/spatial-element c)
     (for [location-category (:LocationCategories c)]
       [:Location
        [:Location_Category (:Category location-category)]
        [:Location_Type (:Type location-category)]
        [:Location_Subregion1 (:Subregion1 location-category)]
        [:Location_Subregion2 (:Subregion2 location-category)]
        [:Location_Subregion3 (:Subregion3 location-category)]
        [:Detailed_Location (:DetailedLocation location-category)]])
     (generate-projects (:Projects c))
     [:Quality (:Quality c)]
     [:Access_Constraints (-> c :AccessConstraints :Description)]
     [:Use_Constraints (:UseConstraints c)]
     (generate-metadata-language c)
     [:Organization
      [:Organization_Type "ARCHIVER"]
      [:Organization_Name
       [:Short_Name "dummy organization short name"]]
      [:Personnel
       [:Role "DATA CENTER CONTACT"]
       [:Contact_Person
        [:Last_Name u/not-provided]]]]
     (for [dist (:Distributions c)]
       [:Distribution
        [:Distribution_Media (:DistributionMedia dist)]
        [:Distribution_Size (u/data-size-str (:Sizes dist))]
        [:Distribution_Format (:DistributionFormat dist)]
        [:Fees (:Fees dist)]])
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
              [:Online_Resource (get-in pub-ref [:RelatedUrl :URLs 0])]
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
     [:Metadata_Name "CEOS IDN DIF"]
     [:Metadata_Version "VERSION 10.2"]
     [:Metadata_Dates
      [:Metadata_Creation "2000-03-24T22:20:41-05:00"]
      [:Metadata_Last_Revision "2000-03-24T22:20:41-05:00"]
      (generate-data-dates c)]
     (generate-additional-attributes (:AdditionalAttributes c))
     [:Product_Level_Id (dif10-product-level-id (-> c :ProcessingLevel :Id))]
     [:Collection_Data_Type (:CollectionDataType c)]
     (when-let [access-value (get-in c [:AccessConstraints :Value])]
       [:Extended_Metadata
        [:Metadata
         [:Name "Restriction"]
         [:Value access-value]]])]))
