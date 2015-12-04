(ns cmr.umm-spec.umm-to-xml-mappings.serf
  "Defines mappings from a UMM record into SERF XML"
  (:require [cmr.umm-spec.xml.gen :refer :all]
            [camel-snake-kebab.core :as csk]))

(def serf-xml-namespaces
  {:xmlns "http://gcmd.gsfc.nasa.gov/Aboutus/xml/serf/"
   :xmlns:serf "http://gcmd.gsfc.nasa.gov/Aboutus/xml/serf/"
   :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
   :xsi:schemaLocation "http://gcmd.gsfc.nasa.gov/Aboutus/xml/serf/ http://gcmd.gsfc.nasa.gov/Aboutus/xml/serf/serf_v9.9.3.xsd"})

(defn umm-s-to-serf-xml
  "Returns SERF XML structure from UMM collection record s."
  [s]
  (xml
    [:SERF
     serf-xml-namespaces
     [:Entry_ID (:EntryId s)]
     [:Entry_Title (:EntryTitle s)]
     [:Service_Citation
      [:Version (:Version s)]]
     (for [sk (:ScienceKeywords s)]
       [:Parameters
        [:Category (:Category sk)]
        [:Topic (:Topic sk)]
        [:Term (:Term sk)]
        [:Variable_Level_1 (:VariableLevel1 sk)]
        [:Variable_Level_2 (:VariableLevel2 sk)]
        [:Variable_Level_3 (:VariableLevel3 sk)]
        [:Detailed_Variable (:DetailedVariable sk)]])
     (for [topic-category (:ISOTopicCategories s)]
       [:ISO_Topic_Category topic-category])
     (for [ak (:AncillaryKeywords s)]
       [:Keyword ak])
     ;;CMR-2269 Needs to be resolved and then we can map instruments to platforms directly. 
     ;; Until then we need to ignore "Not provided"
     (for [platform (:Platforms s) 
           :when (not= (:ShortName platform) "Not provided")]
       [:Source_Name
        [:Short_Name (:ShortName platform)]
        [:Long_Name (:LongName platform)]])
     (for [platform (:Platforms s)
          instrument (:Instruments platform)]
       [:Sensor_Name
        [:Short_Name (:ShortName instrument)]
        [:Long_Name (:LongName instrument)]])
     (for [{:keys [ShortName LongName]} (:Projects s)]
       [:Project
        [:Short_Name ShortName]
        [:Long_Name LongName]])
     [:Quality (:Quality s)]
     [:Access_Constraints (-> s :AccessConstraints :Description)]
     [:Use_Constraints (:UseConstraints s)]
     [:Data_Set_Language (:DataLanguage s)]
     [:Data_Center
      [:Data_Center_Name
       [:Short_Name "datacenter_short_name"]
       [:Long_Name "data center long name"]]
      [:Personnel
       [:Role "DummyRole"]
       [:Last_Name "dummy last name"]]]
     (for [distribution (:Distributions s)]
       [:Distribution
        [:Distribution_Media (:DistributionMedia distribution)]
        [:Distribution_Size (:DistributionSize distribution)]
        [:Distribution_Format (:DistributionFormat distribution)]
        [:Fees (:Fees distribution)]])
     (for [pub-ref (:PublicationReferences s)]
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
      [:Abstract (:Abstract s)]
      [:Purpose (:Purpose s)]]
     (for [related-url (:RelatedUrls s)]
       [:Related_URL
        (when-let [ct (:ContentType related-url)]
          [:URL_Content_Type
           [:Type (:Type ct)]
           [:Subtype (:Subtype ct)]])
        (for [url (:URLs related-url)]
          [:URL url])
        [:Description (:Description related-url)]])
     (for [ma (:MetadataAssociations s)]
       [:Parent_SERF (:EntryId ma)])
     [:Metadata_Name "CEOS IDN ∂DIF"]
     [:Metadata_Version "VERSION 9.9.3"]
     [:Extended_Metadata
      (for [aa (:AdditionalAttributes s)]
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
         [:Value {:type "UpdateDate"} (:UpdateDate aa)]])]]))

