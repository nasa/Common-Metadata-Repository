(ns cmr.umm-spec.umm-to-xml-mappings.dif10
  "Defines mappings from a UMM record into DIF10 XML"
  (:require [cmr.umm-spec.umm-to-xml-mappings.dsl :refer :all]))

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

(def dif10-xml-namespaces
  {:xmlns "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/"
   :xmlns:dif "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/"
   :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
   :xsi:schemaLocation "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/ http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/dif_v10.1.xsd"})

(defn- generate-version
  "Returns content for the version field."
  [xpath-context]
  (let [version (-> xpath-context :context first :Version)]
    (or version "Not provided")))

(defn- generate-platform-type
  "Returns content for the Platform Type field."
  [xpath-context]
  (let [platform (-> xpath-context :context first)]
    (or (get platform-types (:Type platform) "Not provided"))))

(def ^:private temporal-coverage-without-temporal-keywords
  "Returns the temporal coverage content without the temporal keywords"
  [:Temporal_Coverage
   [:Temporal_Range_Type (xpath "TemporalRangeType")]
   [:Precision_Of_Seconds (xpath "PrecisionOfSeconds")]
   [:Ends_At_Present_Flag (xpath "EndsAtPresentFlag")]

   (for-each "RangeDateTimes"
             [:Range_DateTime
              [:Beginning_Date_Time (xpath "BeginningDateTime")]
              [:Ending_Date_Time (xpath "EndingDateTime")]])

   (for-each "SingleDateTimes"
             [:Single_DateTime (xpath ".")])

   (for-each "PeriodicDateTimes"
             [:Periodic_DateTime
              (matching-field :Name)
              [:Start_Date (xpath "StartDate")]
              [:End_Date (xpath "EndDate")]
              [:Duration_Unit (xpath "DurationUnit")]
              [:Duration_Value (xpath "DurationValue")]
              [:Period_Cycle_Duration_Unit (xpath "PeriodCycleDurationUnit")]
              [:Period_Cycle_Duration_Value (xpath "PeriodCycleDurationValue")]])])

(def characteristic-type-mapping
  (matching-object :Characteristics
                   :Name
                   :Description
                   :DataType
                   :Unit
                   :Value))

(def sensor-mapping
  [:Sensor
   [:Short_Name (xpath "ShortName")]
   [:Long_Name (xpath "LongName")]
   [:Technique (xpath "Technique")]
   (for-each "Characteristics"
     characteristic-type-mapping)])

(def umm-c-to-dif10-xml
  [:DIF
   dif10-xml-namespaces
   [:Entry_ID (xpath "/EntryId")]
   [:Version generate-version]
   [:Entry_Title (xpath "/EntryTitle")]
   [:Science_Keywords
    [:Category "dummy category"]
    [:Topic "dummy topic"]
    [:Term "dummy term"]]

   (for-each "/Platforms"
     [:Platform
      [:Type generate-platform-type]
      [:Short_Name (xpath "ShortName")]
      [:Long_Name (xpath "LongName")]
      (for-each "Characteristics"
        characteristic-type-mapping)
      (for-each "Instruments"
        [:Instrument
         [:Short_Name (xpath "ShortName")]
         [:Long_Name (xpath "LongName")]
         [:Technique (xpath "Technique")]
         [:NumberOfSensors (xpath "NumberOfSensors")]
         (for-each "Characteristics"
           characteristic-type-mapping)
         (for-each "OperationalModes"
           [:OperationalMode (xpath ".")])
         (for-each "Sensors"
           sensor-mapping)])])

   ;; DIF10 has TemporalKeywords bundled together with TemporalExtents in the Temporal_Coverage
   ;; element. There is no clear definition on which TemporalExtent the TemporalKeywords should
   ;; be associated with. This is something DIF10 team will look into at improving, but in the
   ;; mean time, we put the TemporalKeywords on the first TemporalExtent element.
   (for-each "/TemporalExtents[1]"
             (conj temporal-coverage-without-temporal-keywords
                   [:Temporal_Info
                    (for-each "/TemporalKeywords"
                              [:Ancillary_Temporal_Keyword (xpath ".")])]))
   (for-each "/TemporalExtents[2..]"
             temporal-coverage-without-temporal-keywords)

   [:Spatial_Coverage
    [:Granule_Spatial_Representation "GEODETIC"]]
   [:Project
    [:Short_Name "dummy project short name"]]
   [:Quality (xpath "/Quality")]
   [:Access_Constraints (xpath "/AccessConstraints/Description")]
   [:Use_Constraints (xpath "/UseConstraints")]
   [:Dataset_Language (xpath "/DataLanguage")]
   [:Organization
    [:Organization_Type "ARCHIVER"]
    [:Organization_Name
     [:Short_Name "dummy organization short name"]]
    [:Personnel
     [:Role "DATA CENTER CONTACT"]
     [:Contact_Person
      [:Last_Name "Not provided"]]]]
   [:Summary
    [:Abstract (xpath "/Abstract")]
    [:Purpose (xpath "/Purpose")]]
   [:Related_URL
    [:URL "http://www.foo.com"]]
   [:Metadata_Name "CEOS IDN DIF"]
   [:Metadata_Version "VERSION 10.1"]
   [:Metadata_Dates
    [:Metadata_Creation "2000-03-24T22:20:41-05:00"]
    [:Metadata_Last_Revision "2000-03-24T22:20:41-05:00"]
    [:Data_Creation "1970-01-01T00:00:00"]
    [:Data_Last_Revision "1970-01-01T00:00:00"]]
   (for-each "/AdditionalAttributes"
    (matching-object :AdditionalAttributes :Name :DataType :Description :MeasurementResolution
                     :ParameterRangeBegin :ParameterRangeEnd :ParameterUnitsOfMeasure
                     :ParameterValueAccuracy :ValueAccuracyExplanation :Value))
   [:Product_Flag "Not provided"]])

