(ns cmr.umm-spec.xml-to-umm-mappings.dif9
  "Defines mappings from DIF9 XML into UMM records"
  (:require [cmr.umm-spec.simple-xpath :refer [select text]]
            [cmr.umm-spec.xml.parse :refer :all]
            [cmr.umm-spec.json-schema :as js]))

(defn- parse-dif9-xml
  "Returns collection map from DIF9 collection XML document."
  [doc]
  {:EntryTitle (value-of doc "/DIF/Entry_Title")
   :EntryId (value-of doc "/DIF/Entry_ID")
   :Version (value-of doc "/DIF/Data_Set_Citation/Version")
   :Abstract (value-of doc "/DIF/Summary/Abstract")
   :CollectionDataType (value-of doc "/DIF/Extended_Metadata/Metadata[Name='CollectionDataType']/Value")
   :Purpose (value-of doc "/DIF/Summary/Purpose")
   :DataLanguage (value-of doc "/DIF/Data_Set_Language")
   :TemporalKeywords (values-at doc "/DIF/Data_Resolution/Temporal_Resolution")
   :Projects (for [proj (select doc "/DIF/Project")]
                {:ShortName (value-of proj "Short_Name")
                 :LongName (value-of proj "Long_Name")})
   :CollectionProgress (value-of doc "/DIF/Data_Set_Progress")
   :SpatialKeywords (values-at doc "/DIF/Location")
   :Quality (value-of doc "/DIF/Quality")
   :AccessConstraints {:Description (value-of doc "/DIF/Access_Constraints")}
   :UseConstraints (value-of doc "/DIF/Use_Constraints")
   :Platforms (for [platform (select doc "/DIF/Source_Name")]
                {:ShortName (value-of platform "Short_Name")
                 :LongName (value-of platform "Long_Name")})
   :TemporalExtents (when-let [temporals (select doc "/DIF/Temporal_Coverage")]
                      [{:RangeDateTimes (for [temporal temporals]
                                          {:BeginningDateTime (value-of temporal "Start_Date")
                                           :EndingDateTime    (value-of temporal "Stop_Date")})}])
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
                            :UpdateDate (value-of aa "Value[@type='UpdateDate']")})})

(defn dif9-xml-to-umm-c
  "Returns UMM-C collection record from DIF9 collection XML document."
  [metadata]
  (js/coerce (parse-dif9-xml metadata)))