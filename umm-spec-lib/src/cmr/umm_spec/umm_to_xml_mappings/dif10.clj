(ns cmr.umm-spec.umm-to-xml-mappings.dif10
  "Defines mappings from a UMM record into DIF10 XML"
  (:require [cmr.umm-spec.umm-to-xml-mappings.dsl :refer :all]))

(def dif10-xml-namespaces
  {:xmlns "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/"
   :xmlns:dif "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/"
   :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
   :xsi:schemaLocation "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/ http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/dif_v10.1.xsd"})

(def umm-c-to-dif10-xml
  [:DIF
   dif10-xml-namespaces
   [:Entry_ID (xpath "/EntryId")]
   [:Version (xpath "/Version")]
   [:Entry_Title (xpath "/EntryTitle")]
   [:Science_Keywords
    [:Category "dummy category"]
    [:Topic "dummy topic"]
    [:Term "dummy term"]]
   [:Platform
    [:Type "Not provided"]
    [:Short_Name "dummy platform short name"]
    [:Instrument
     [:Short_Name "dummy instrument short name"]]]

   (for-each "/TemporalExtents"
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
         [:Name (xpath "Name")]
         [:Start_Date (xpath "StartDate")]
         [:End_Date (xpath "EndDate")]
         [:Duration_Unit (xpath "DurationUnit")]
         [:Duration_Value (xpath "DurationValue")]
         [:Period_Cycle_Duration_Unit (xpath "PeriodCycleDurationUnit")]
         [:Period_Cycle_Duration_Value (xpath "PeriodCycleDurationValue")]])])

   [:Spatial_Coverage
    [:Granule_Spatial_Representation "GEODETIC"]]
   [:Project
    [:Short_Name "dummy project short name"]]
   [:Quality (xpath "/Quality")]
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
   [:Product_Flag "Not provided"]])
