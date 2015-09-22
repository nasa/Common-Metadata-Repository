(ns cmr.umm-spec.umm-to-xml-mappings.dif9
  "Defines mappings from a UMM record into DIF9 XML"
  (:require [cmr.umm-spec.xml.gen :refer :all]
            [camel-snake-kebab.core :as csk]))

(def dif9-xml-namespaces
  {:xmlns "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/"
   :xmlns:dif "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/"
   :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
   :xsi:schemaLocation "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/ http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/dif_v9.9.3.xsd"})

(defn umm-c-to-dif9-xml
  "Returns DIF9 XML structure from UMM collection record c."
  [c]
  (xml
    [:DIF
     dif9-xml-namespaces
     [:Entry_ID (:EntryId c)]
     [:Entry_Title (:EntryTitle c)]
     [:Data_Set_Citation
      [:Version (:Version c)]]
     [:Parameters
      [:Category "dummy category"]
      [:Topic "dummy topic"]
      [:Term "dummy term"]]
     [:ISO_Topic_Category "dummy iso topic category"]
     (for [ak (:AncillaryKeywords c)]
       [:Keyword ak])
     (for [platform (:Platforms c)]
       [:Source_Name
        [:Short_Name (:ShortName platform)]
        [:Long_Name (:LongName platform)]])
     (for [temporal (:TemporalExtents c)
           rdt (:RangeDateTimes temporal)]
       [:Temporal_Coverage
        [:Start_Date (:BeginningDateTime rdt)]
        [:Stop_Date (:EndingDateTime rdt)]])
     (for [temporal (:TemporalExtents c)
           sdt (:SingleDateTimes temporal)]
       [:Temporal_Coverage
        [:Start_Date sdt]
        [:Stop_Date sdt]])
     [:Data_Set_Progress (:CollectionProgress c)]
     (for [mbr (-> c :SpatialExtent :HorizontalSpatialDomain :Geometry :BoundingRectangles)]
       [:Spatial_Coverage
        [:Southernmost_Latitude (:SouthBoundingCoordinate mbr)]
        [:Northernmost_Latitude (:NorthBoundingCoordinate mbr)]
        [:Westernmost_Longitude (:WestBoundingCoordinate mbr)]
        [:Easternmost_Longitude (:EastBoundingCoordinate mbr)]])
     (for [spatial-keyword (:SpatialKeywords c)]
       [:Location
        [:Location_Category spatial-keyword]])
     (for [temproal-keywod (:TemporalKeywords c)]
       [:Data_Resolution
        [:Temporal_Resolution temproal-keywod]])
     (for [{:keys [ShortName LongName]} (:Projects c)]
       [:Project
        [:Short_Name ShortName]
        [:Long_Name LongName]])
     [:Quality (:Quality c)]
     [:Access_Constraints (-> c :AccessConstraints :Description)]
     [:Use_Constraints (:UseConstraints c)]
     [:Data_Set_Language (:DataLanguage c)]
     [:Data_Center
      [:Data_Center_Name
       [:Short_Name "datacenter_short_name"]
       [:Long_Name "data center long name"]]
      [:Personnel
       [:Role "DummyRole"]
       [:Last_Name "dummy last name"]]]
     (for [distribution (:Distributions c)]
       [:Distribution
        [:Distribution_Media (:DistributionMedia distribution)]
        [:Distribution_Size (:DistributionSize distribution)]
        [:Distribution_Format (:DistributionFormat distribution)]
        [:Fees (:Fees distribution)]])
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
              [:DOI (get-in pub-ref [:DOI :DOI])]
              [:Online_Resource (get-in pub-ref [:RelatedUrl :URLs 0])]
              :Other_Reference_Details])])
     [:Summary
      [:Abstract (:Abstract c)]
      [:Purpose (:Purpose c)]]
     [:Metadata_Name "CEOS IDN DIF"]
     [:Metadata_Version "VERSION 9.9.3"]
     [:Extended_Metadata
      (for [aa (:AdditionalAttributes c)]
        [:Metadata
         [:Group "AdditionalAttribute"]
         [:Name (:Name aa)]
         [:Description (:Description aa)]
         [:Type (:DataType aa)]
         [:Value {:type "Value"} (:Value aa)]
         [:Value {:type "ParamRangeBegin"} (:ParameterRangeBegin aa)]
         [:Value {:type "ParamRangeEnd"} (:ParameterRangeEnd aa)]
         [:Value {:type "MeasurementResolution"} (:MeasurementResolution aa)]
         [:Value {:type "ParameterUnitsOfMeasure"} (:ParameterUnitsOfMeasure aa)]
         [:Value {:type "ParameterValueAccuracy"} (:ParameterValueAccuracy aa)]
         [:Value {:type "ValueAccuracyExplanation"} (:ValueAccuracyExplanation aa)]
         [:Value {:type "UpdateDate"} (:UpdateDate aa)]])
      (when-let [collection-data-type (:CollectionDataType c)]
        [:Metadata
         [:Group "ECHO"]
         [:Name "CollectionDataType"]
         [:Value collection-data-type]])
      [:Metadata
       [:Name "ProcessingLevelId"]
       [:Value (-> c :ProcessingLevel :Id)]]
      [:Metadata
       [:Name "ProcessingLevelDescription"]
       [:Value (-> c :ProcessingLevel :ProcessingLevelDescription)]]]]))


