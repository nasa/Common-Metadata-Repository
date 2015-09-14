(ns cmr.umm-spec.umm-to-xml-mappings.dif10
  "Defines mappings from a UMM record into DIF10 XML"
  (:require [cmr.umm-spec.xml.gen :refer :all]))

(def platform-types
  "The set of values that DIF 10 defines for platform types as enumerations in its schema"
  #{"Not provided"
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
  #{"Not provided"
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
   :xsi:schemaLocation "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/ http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/dif_v10.1.xsd"})

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

(defn umm-c-to-dif10-xml
  "Returns DIF10 XML from a UMM-C collection record."
  [c]
  (xml
   [:DIF
    dif10-xml-namespaces
    [:Entry_ID (:EntryId c)]
    [:Version (or (:Version c) "Not provided")]
    [:Entry_Title (:EntryTitle c)]
    [:Science_Keywords
     [:Category "dummy category"]
     [:Topic "dummy topic"]
     [:Term "dummy term"]]

    (for [platform (:Platforms c)]
      [:Platform
       [:Type (get platform-types (:Type platform) "Not provided")]
       [:Short_Name (:ShortName platform)]
       [:Long_Name (:LongName platform)]
       (characteristics-for platform)
       (for [instrument (:Instruments platform)]
         [:Instrument
          [:Short_Name (:ShortName instrument)]
          [:Long_Name (:LongName instrument)]
          [:Technique (:Technique instrument)]
          [:NumberOfSensors (:NumberOfSensors instrument)]
          (characteristics-for instrument)
          (for [opmode (:OperationalModes instrument)]
            [:OperationalMode opmode])
          (map sensor-mapping (:Sensors instrument))])])

    ;; DIF10 has TemporalKeywords bundled together with TemporalExtents in the Temporal_Coverage
    ;; element. There is no clear definition on which TemporalExtent the TemporalKeywords should
    ;; be associated with. This is something DIF10 team will look into at improving, but in the
    ;; mean time, we put the TemporalKeywords on the first TemporalExtent element.
    (when-let [extent (-> c :TemporalExtents first)]
      (conj (temporal-coverage-without-temporal-keywords extent)
            [:Temporal_Info
             (for [tkw (:TemporalKeywords c)]
               [:Ancillary_Temporal_Keyword tkw])]))

    (map temporal-coverage-without-temporal-keywords (drop 1 (:TemporalExtents c)))

    [:Data_Set_Progress (:CollectionProgress c)]
    [:Spatial_Coverage
     [:Granule_Spatial_Representation "GEODETIC"]]
    (for [skw (:SpatialKeywords c)]
      [:Location skw])
    [:Project
     [:Short_Name "dummy project short name"]]
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
       [:Last_Name "Not provided"]]]]
    (for [dist (:Distributions c)]
      [:Distribution
       [:Distribution_Media (:DistributionMedia dist)]
       [:Distribution_Size (:DistributionSize dist)]
       [:Distribution_Format (:DistributionFormat dist)]
       [:Fees (:Fees dist)]])
    [:Summary
     [:Abstract (:Abstract c)]
     [:Purpose (:Purpose c)]]
    [:Related_URL
     [:URL "http://www.foo.com"]]
    [:Metadata_Name "CEOS IDN DIF"]
    [:Metadata_Version "VERSION 10.1"]
    [:Metadata_Dates
     [:Metadata_Creation "2000-03-24T22:20:41-05:00"]
     [:Metadata_Last_Revision "2000-03-24T22:20:41-05:00"]
     [:Data_Creation "1970-01-01T00:00:00"]
     [:Data_Last_Revision "1970-01-01T00:00:00"]]
    (for [aa (:AdditionalAttributes c)]
      [:AdditionalAttributes
       (elements-from aa
                      :Name :DataType :Description :MeasurementResolution
                      :ParameterRangeBegin :ParameterRangeEnd :ParameterUnitsOfMeasure
                      :ParameterValueAccuracy :ValueAccuracyExplanation :Value)])
    [:Product_Level_Id (get product-levels (-> c :ProcessingLevel :Id))]
    [:Collection_Data_Type (:CollectionDataType c)]
    [:Product_Flag "Not provided"]]))
