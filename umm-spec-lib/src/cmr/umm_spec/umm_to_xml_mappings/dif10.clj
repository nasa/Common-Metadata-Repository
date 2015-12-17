(ns cmr.umm-spec.umm-to-xml-mappings.dif10
  "Defines mappings from a UMM record into DIF10 XML"
  (:require [cmr.umm-spec.xml.gen :refer :all]
            [cmr.umm-spec.umm-to-xml-mappings.dif10.spatial :as spatial]
            [cmr.umm-spec.date-util :as date]
            [camel-snake-kebab.core :as csk]
            [clj-time.format :as f]
            [cmr.umm-spec.util :as u :refer [with-default]]))

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
  #{u/not-provided
    "Level 0"
    "Level 1"
    "Level 1A"
    "Level 1B"
    "Level 1T"
    "Level 2"
    "Level 2G"
    "Level 2P"
    "Level 3"
    "Level 4"
    "Level NA"})

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
     [:ParameterRangeBegin (with-default (:ParameterRangeBegin aa))]
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

     (if-let [cats (:ISOTopicCategories c)]
       (for [topic-category cats]
         [:ISO_Topic_Category topic-category])
       [:ISO_Topic_Category u/not-provided])

     (if-let [aks (:AncillaryKeywords c)]
       (for [ak aks]
         [:Ancillary_Keyword ak])
       [:Ancillary_Keyword u/not-provided])

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
         [:Beginning_Date_Time u/not-provided]
         [:Ending_Date_Time u/not-provided]]])
     
     (map temporal-coverage-without-temporal-keywords (drop 1 (:TemporalExtents c)))

     [:Dataset_Progress (:CollectionProgress c)]
     (spatial/spatial-element c)
     (for [skw (:SpatialKeywords c)]
       [:Location
        [:Location_Category skw]])
     (generate-projects (:Projects c))
     [:Quality (:Quality c)]
     [:Access_Constraints (-> c :AccessConstraints :Description)]
     [:Use_Constraints (:UseConstraints c)]
     [:Dataset_Language (:DataLanguage c)]
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
        [:Distribution_Size (:DistributionSize dist)]
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
      [:Abstract (u/with-default (:Abstract c))]
      [:Purpose (u/with-default (:Purpose c))]]

     (if-let [urls (:RelatedUrls c)]
       (for [related-url urls]
         [:Related_URL
          (when-let [ct (:ContentType related-url)]
            [:URL_Content_Type
             [:Type (:Type ct)]
             [:Subtype (:Subtype ct)]])
          [:Protocol (:Protocol related-url)]
          (for [url (get related-url :URLs ["http://www.foo.com"])]
            [:URL url])
          [:Description (:Description related-url)]])
       [:Related_URL
        [:URL "http://example.com"]])
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
     [:Product_Level_Id (get product-levels (-> c :ProcessingLevel :Id))]
     [:Collection_Data_Type (:CollectionDataType c)]
     (when-let [access-value (get-in c [:AccessConstraints :Value])]
       [:Extended_Metadata
        [:Metadata
         [:Name "Restriction"]
         [:Value access-value]]])])
  )
