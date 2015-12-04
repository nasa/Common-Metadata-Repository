(ns cmr.umm-spec.xml-to-umm-mappings.dif9
  "Defines mappings from DIF9 XML into UMM records"
  (:require [cmr.umm-spec.simple-xpath :refer [select text]]
            [cmr.umm-spec.xml.parse :refer :all]
            [camel-snake-kebab.core :as csk]
            [cmr.common.util :as util]
            [cmr.umm-spec.util :as umm-util]
            [cmr.umm-spec.json-schema :as js]))

(defn- parse-mbrs
  "Returns a seq of bounding rectangle maps in the given DIF XML doc."
  [doc]
  (for [el (select doc "/DIF/Spatial_Coverage")]
    {:NorthBoundingCoordinate (value-of el "Northernmost_Latitude")
     :SouthBoundingCoordinate (value-of el "Southernmost_Latitude")
     :WestBoundingCoordinate (value-of el "Westernmost_Longitude")
     :EastBoundingCoordinate (value-of el "Easternmost_Longitude")}))

(defn- parse-dif9-xml
  "Returns collection map from DIF9 collection XML document."
  [doc]
  {:EntryTitle (value-of doc "/DIF/Entry_Title")
   :ShortName (value-of doc "/DIF/Entry_ID")
   :Version (or (value-of doc "/DIF/Data_Set_Citation/Version") umm-util/not-provided)
   :Abstract (value-of doc "/DIF/Summary/Abstract")
   :CollectionDataType (value-of doc "/DIF/Extended_Metadata/Metadata[Name='CollectionDataType']/Value")
   :Purpose (value-of doc "/DIF/Summary/Purpose")
   :DataLanguage (value-of doc "/DIF/Data_Set_Language")
   :ISOTopicCategories (values-at doc "DIF/ISO_Topic_Category")
   :TemporalKeywords (values-at doc "/DIF/Data_Resolution/Temporal_Resolution")
   :Projects (for [proj (select doc "/DIF/Project")]
               {:ShortName (value-of proj "Short_Name")
                :LongName (value-of proj "Long_Name")})
   :CollectionProgress (value-of doc "/DIF/Data_Set_Progress")
   :SpatialKeywords (values-at doc "/DIF/Location/Location_Category")
   :Quality (value-of doc "/DIF/Quality")
   :AccessConstraints {:Description (value-of doc "/DIF/Access_Constraints")
                       :Value (value-of doc "/DIF/Extended_Metadata/Metadata[Name='Restriction']/Value")}
   :UseConstraints (value-of doc "/DIF/Use_Constraints")
   :Platforms (for [platform (select doc "/DIF/Source_Name")]
                {:ShortName (value-of platform "Short_Name")
                 :LongName (value-of platform "Long_Name")})
   :TemporalExtents (when-let [temporals (select doc "/DIF/Temporal_Coverage")]
                      [{:RangeDateTimes (for [temporal temporals]
                                          {:BeginningDateTime (value-of temporal "Start_Date")
                                           :EndingDateTime    (value-of temporal "Stop_Date")})}])
   :SpatialExtent {:HorizontalSpatialDomain {:Geometry {:BoundingRectangles (parse-mbrs doc)}}}
   :Distributions (for [distribution (select doc "/DIF/:Distribution")]
                    {:DistributionMedia (value-of distribution "Distribution_Media")
                     :DistributionSize (value-of distribution "Distribution_Size")
                     :DistributionFormat (value-of distribution "Distribution_Format")
                     :Fees (value-of distribution "Fees")})
   :ProcessingLevel {:Id
                     (value-of doc "/DIF/Extended_Metadata/Metadata[Name='ProcessingLevelId']/Value")

                     :ProcessingLevelDescription
                     (value-of doc "/DIF/Extended_Metadata/Metadata[Name='ProcessingLevelDescription']/Value")}

   :AdditionalAttributes (for [aa (select doc "/DIF/Extended_Metadata/Metadata[Group='AdditionalAttribute']")]
                           {:Name (value-of aa "Name")
                            :Description (value-of aa "Description")
                            :DataType (value-of aa "Type")
                            :Group "AdditionalAttribute"
                            :ParameterRangeBegin (value-of aa "Value[@type='ParamRangeBegin']")
                            :ParameterRangeEnd (value-of aa "Value[@type='ParamRangeEnd']")
                            :Value (value-of aa "Value[@type='Value']")
                            :MeasurementResolution (value-of aa "Value[@type='MeasurementResolution']")
                            :ParameterUnitsOfMeasure (value-of aa "Value[@type='ParameterUnitsOfMeasure']")
                            :ParameterValueAccuracy (value-of aa "Value[@type='ParameterValueAccuracy']")
                            :ValueAccuracyExplanation (value-of aa "Value[@type='ValueAccuracyExplanation']")
                            :UpdateDate (value-of aa "Value[@type='UpdateDate']")})
  :PublicationReferences (for [pub-ref (select doc "/DIF/Reference")]
                          (into {} (map (fn [x]
                                          (if (keyword? x)
                                            [(csk/->PascalCaseKeyword x) (value-of pub-ref (str x))]
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
                                         [:ISBN (value-of pub-ref "ISBN")]
                                         [:DOI {:DOI (value-of pub-ref "DOI")}]
                                         [:RelatedUrl
                                          {:URLs (seq
                                                   (remove nil? [(value-of pub-ref "Online_Resource")]))}]
                                         :Other_Reference_Details])))
  :AncillaryKeywords (values-at doc  "/DIF/Keyword")
  :ScienceKeywords (for [sk (select doc "/DIF/Parameters")]
                         {:Category (value-of sk "Category")
                          :Topic (value-of sk "Topic")
                          :Term (value-of sk "Term")
                          :VariableLevel1 (value-of sk "Variable_Level_1")
                          :VariableLevel2 (value-of sk "Variable_Level_2")
                          :VariableLevel3 (value-of sk "Variable_Level_3")
                          :DetailedVariable (value-of sk "Detailed_Variable")})
  :RelatedUrls (for [related-url (select doc "/DIF/Related_URL")
                      :let [description (value-of related-url "Description")]]
                  {:URLs (values-at related-url "URL")
                   :Description description
                   :ContentType {:Type (value-of related-url "URL_Content_Type/Type")
                                 :Subtype (value-of related-url "URL_Content_Type/Subtype")}})
  :MetadataAssociations (for [parent-dif (values-at doc "/DIF/Parent_DIF")]
                          {:EntryId parent-dif})})

(defn dif9-xml-to-umm-c
  "Returns UMM-C collection record from DIF9 collection XML document."
  [metadata]
  (js/coerce (parse-dif9-xml metadata)))
