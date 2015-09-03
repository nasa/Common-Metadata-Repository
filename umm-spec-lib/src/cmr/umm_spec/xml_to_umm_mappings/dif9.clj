(ns cmr.umm-spec.xml-to-umm-mappings.dif9
  "Defines mappings from DIF9 XML into UMM records"
  (:require [cmr.umm-spec.xml-to-umm-mappings.dsl :refer :all]
            [cmr.umm-spec.xml-to-umm-mappings.add-parse-type :as apt]
            [cmr.umm-spec.json-schema :as js]))

(def dif9-xml-to-umm-c
  (apt/add-parsing-types
    js/umm-c-schema
    (object
      {:EntryTitle (xpath "/DIF/Entry_Title")
       :EntryId (xpath "/DIF/Entry_ID")
       :Version (xpath "/DIF/Data_Set_Citation/Version")
       :Abstract (xpath "/DIF/Summary/Abstract")
       :CollectionDataType (xpath "/DIF/Extended_Metadata/Metadata[Name='CollectionDataType']/Value")
       :Purpose (xpath "/DIF/Summary/Purpose")
       :DataLanguage (xpath "/DIF/Data_Set_Language")
       :TemporalKeywords (for-each "/DIF/Data_Resolution"
                                   (xpath "Temporal_Resolution"))
       :Quality (xpath "/DIF/Quality")
       :AccessConstraints (object
                            {:Description (xpath "/DIF/Access_Constraints")})
       :UseConstraints (xpath "/DIF/Use_Constraints")
       :Platforms (for-each "/DIF/Source_Name"
                    (object {:ShortName (xpath "Short_Name")
                             :LongName (xpath "Long_Name")}))
       :TemporalExtents (for-each "."
                          (object {:RangeDateTimes (for-each "/DIF/Temporal_Coverage"
                                                     (object {:BeginningDateTime (xpath "Start_Date")
                                                              :EndingDateTime    (xpath "Stop_Date")}))}))
       :AdditionalAttributes (for-each "/DIF/Extended_Metadata/Metadata[Group='AdditionalAttribute']"
                               (object {:Name (xpath "Name")
                                        :Description (xpath "Description")
                                        :DataType (xpath "Type")
                                        :Group (constant "AdditionalAttribute")
                                        :ParameterRangeBegin (xpath "Value[@type='ParamRangeBegin']")
                                        :ParameterRangeEnd (xpath "Value[@type='ParamRangeEnd']")
                                        :Value (xpath "Value[@type='Value']")
                                        :MeasurementResolution (xpath "Value[@type='MeasurementResolution']")
                                        :ParameterUnitsOfMeasure (xpath "Value[@type='ParameterUnitsOfMeasure']")
                                        :ParameterValueAccuracy (xpath "Value[@type='ParameterValueAccuracy']")
                                        :ValueAccuracyExplanation (xpath "Value[@type='ValueAccuracyExplanation']")
                                        :UpdateDate (xpath "Value[@type='UpdateDate']")}))})))

