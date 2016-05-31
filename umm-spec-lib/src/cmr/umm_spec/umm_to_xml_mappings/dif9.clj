(ns cmr.umm-spec.umm-to-xml-mappings.dif9
  "Defines mappings from a UMM record into DIF9 XML"
  (:require [cmr.umm-spec.util :as u]
            [cmr.common.xml.gen :refer :all]
            [clojure.set :as set]
            [camel-snake-kebab.core :as csk]
            [cmr.umm-spec.xml-to-umm-mappings.dif9 :as xtu]))

(def dif9-xml-namespaces
  {:xmlns "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/"
   :xmlns:dif "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/"
   :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
   :xsi:schemaLocation "http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/ http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/dif_v9.9.3.xsd"})

(def umm-iso-topic-category->dif-iso-topic-category
  "UMM ISOTopicCategory to DIF ISOTopicCategory mapping."
  (set/map-invert xtu/dif-iso-topic-category->umm-iso-topic-category))

(defn- generate-short-name-long-name-elements
  "Returns xml elements with the given elem-key as name and sub-elements with Short_Name and
  Long_Name as defined in values."
  [elem-key values]
  (for [value values]
    [elem-key
     [:Short_Name (:ShortName value)]
     [:Long_Name (:LongName value)]]))

(defn generate-instruments
  [platforms]
  (let [instruments (distinct (mapcat :Instruments platforms))]
    (generate-short-name-long-name-elements :Sensor_Name instruments)))

(defn generate-platforms
  [platforms]
  (generate-short-name-long-name-elements :Source_Name platforms))

(defn umm-c-to-dif9-xml
  "Returns DIF9 XML structure from UMM collection record c."
  [c]
  (xml
    [:DIF
     dif9-xml-namespaces
     [:Entry_ID (if (or (nil? (:Version c))
                        (= u/not-provided (:Version c)))
                  (:ShortName c)
                  (str (:ShortName c) "_" (:Version c)))]
     [:Entry_Title (:EntryTitle c)]
     [:Data_Set_Citation
      [:Version (:Version c)]]
     (if-let [sks (:ScienceKeywords c)]
       (for [sk sks]
         [:Parameters
          [:Category (:Category sk)]
          [:Topic (:Topic sk)]
          [:Term (:Term sk)]
          [:Variable_Level_1 (:VariableLevel1 sk)]
          [:Variable_Level_2 (:VariableLevel2 sk)]
          [:Variable_Level_3 (:VariableLevel3 sk)]
          [:Detailed_Variable (:DetailedVariable sk)]])
       ;; Default element
       [:Parameters
        [:Category u/not-provided]
        [:Topic u/not-provided]
        [:Term u/not-provided]])
     (for [topic-category (:ISOTopicCategories c)]
       [:ISO_Topic_Category topic-category])
     (for [ak (:AncillaryKeywords c)]
       [:Keyword ak])
     (generate-instruments (:Platforms c))
     (generate-platforms (:Platforms c))
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
     (for [paleo (:PaleoTemporalCoverages c)
           :let [{:keys [StartDate EndDate ChronostratigraphicUnits]} paleo]]
       [:Paleo_Temporal_Coverage
        [:Paleo_Start_Date StartDate]
        [:Paleo_Stop_Date EndDate]
        (for [{:keys [Eon Era Period Epoch Stage DetailedClassification]} ChronostratigraphicUnits]
          [:Chronostratigraphic_Unit
           [:Eon Eon]
           [:Era Era]
           [:Period Period]
           [:Epoch Epoch]
           [:Stage Stage]
           [:Detailed_Classification DetailedClassification]])])
     [:Data_Set_Progress (:CollectionProgress c)]
     (for [mbr (-> c :SpatialExtent :HorizontalSpatialDomain :Geometry :BoundingRectangles)]
       [:Spatial_Coverage
        [:Southernmost_Latitude (:SouthBoundingCoordinate mbr)]
        [:Northernmost_Latitude (:NorthBoundingCoordinate mbr)]
        [:Westernmost_Longitude (:WestBoundingCoordinate mbr)]
        [:Easternmost_Longitude (:EastBoundingCoordinate mbr)]])
     (let [location-keywords (:LocationKeywords c)]
       (for [lk location-keywords]
         [:Location
          [:Location_Category (:Category lk)]
          [:Location_Type (:Type lk)]
          [:Location_Subregion1 (:Subregion1 lk)]
          [:Location_Subregion2 (:Subregion2 lk)]
          [:Location_Subregion3 (:Subregion3 lk)]
          [:Detailed_Location (:DetailedLocation lk)]]))
     (for [temporal-keyword (:TemporalKeywords c)]
       [:Data_Resolution
        [:Temporal_Resolution temporal-keyword]])
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
        [:Distribution_Size (u/data-size-str (:Sizes distribution))]
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
     (for [related-url (:RelatedUrls c)]
       [:Related_URL
        (when-let [[type subtype] (:Relation related-url)]
          [:URL_Content_Type
           [:Type type]
           [:Subtype subtype]])
        (for [url (:URLs related-url)]
          [:URL url])
        [:Description (:Description related-url)]])
     (for [ma (:MetadataAssociations c)]
       [:Parent_DIF (:EntryId ma)])
     [:Metadata_Name "CEOS IDN DIF"]
     [:Metadata_Version "VERSION 9.9.3"]
     [:Extended_Metadata
      (for [{:keys [Group Name Description DataType Value ParamRangeBegin ParamRangeEnd UpdateDate]}
            (:AdditionalAttributes c)
            ;; DIF9 does not support ranges in Extended_Metadata - Order of preference for the value
            ;; is value, then parameter-range-begin, then parameter-range-end.
            :let [aa-value (or Value ParamRangeBegin ParamRangeEnd)]]
        [:Metadata
         [:Group Group]
         [:Name Name]
         [:Description Description]
         [:Type DataType]
         [:Update_Date UpdateDate]
         [:Value {} aa-value]])
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
       [:Value (-> c :ProcessingLevel :ProcessingLevelDescription)]]
      [:Metadata
       [:Name "GranuleSpatialRepresentation"]
       [:Value (-> c :SpatialExtent :GranuleSpatialRepresentation)]]
      (when-let [access-value (get-in c [:AccessConstraints :Value])]
        [:Metadata
         [:Name "Restriction"]
         [:Value access-value]])]]))
