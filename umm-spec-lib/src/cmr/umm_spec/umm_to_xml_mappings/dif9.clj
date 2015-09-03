(ns cmr.umm-spec.umm-to-xml-mappings.dif9
  "Defines mappings from a UMM record into DIF9 XML"
  (:require [cmr.umm-spec.umm-to-xml-mappings.dsl :refer :all]))

(def dif9-xml-namespaces
  {:xmlns "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/"
   :xmlns:dif "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/"
   :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
   :xsi:schemaLocation "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/ http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/dif_v9.9.3.xsd"})

(defn- generate-collection-data-type
  "Returns content generator instruction for the CollectionDataType field. We create this function
  because we don't want to generate the sibling elements when there is no CollectionDataType value."
  [xpath-context]
  (when-let [collection-data-type (-> xpath-context :context first :CollectionDataType)]
    [[:Group "ECHO"]
     [:Name "CollectionDataType"]
     [:Value collection-data-type]]))

(def umm-c-to-dif9-xml
  [:DIF
   dif9-xml-namespaces
   [:Entry_ID (xpath "/EntryId")]
   [:Entry_Title (xpath "/EntryTitle")]
   [:Data_Set_Citation
    [:Version (xpath "/Version")]]
   [:Parameters
    [:Category "dummy category"]
    [:Topic "dummy topic"]
    [:Term "dummy term"]]
   [:ISO_Topic_Category "dummy iso topic category"]
   (for-each "/Platforms"
             [:Source_Name
              [:Short_Name (xpath "ShortName")]
              [:Long_Name (xpath "LongName")]])
   (for-each "/TemporalExtents/RangeDateTimes"
             [:Temporal_Coverage
              [:Start_Date (xpath "BeginningDateTime")]
              [:Stop_Date (xpath "EndingDateTime")]])
   (for-each "/TemporalExtents/SingleDateTimes"
             [:Temporal_Coverage
              [:Start_Date (xpath ".")]
              [:Stop_Date (xpath ".")]])
   (for-each "/TemporalKeywords"
             [:Data_Resolution
              [:Temporal_Resolution (xpath ".")]])
   [:Quality (xpath "/Quality")]
   [:Access_Constraints (xpath "/AccessConstraints/Description")]
   [:Use_Constraints (xpath "/UseConstraints")]
   [:Data_Set_Language (xpath "/DataLanguage")]
   [:Data_Center
    [:Data_Center_Name
     [:Short_Name "datacenter_short_name"]
     [:Long_Name "data center long name"]]
    [:Personnel
     [:Role "DummyRole"]
     [:Last_Name "dummy last name"]]]
   [:Summary
    [:Abstract (xpath "/Abstract")]
    [:Purpose (xpath "/Purpose")]]
   [:Metadata_Name "CEOS IDN DIF"]
   [:Metadata_Version "VERSION 9.9.3"]
   [:Extended_Metadata
    (for-each "/AdditionalAttributes"
              [:Metadata
               [:Group "AdditionalAttribute"]
               [:Name (xpath "Name")]
               [:Description (xpath "Description")]
               [:Type (xpath "DataType")]
               [:Value {:type "Value"} (xpath "Value")]
               [:Value {:type "ParamRangeBegin"} (xpath "ParameterRangeBegin")]
               [:Value {:type "ParamRangeEnd"} (xpath "ParameterRangeEnd")]
               [:Value {:type "MeasurementResolution"} (xpath "MeasurementResolution")]
               [:Value {:type "ParameterUnitsOfMeasure"} (xpath "ParameterUnitsOfMeasure")]
               [:Value {:type "ParameterValueAccuracy"} (xpath "ParameterValueAccuracy")]
               [:Value {:type "ValueAccuracyExplanation"} (xpath "ValueAccuracyExplanation")]
               [:Value {:type "UpdateDate"} (xpath "UpdateDate")]])
    [:Metadata generate-collection-data-type]]])


