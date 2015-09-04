(ns cmr.umm-spec.xml-to-umm-mappings.dif10
  "Defines mappings from DIF10 XML into UMM records"
  (:require [cmr.common.xml :as cx]
            [cmr.umm-spec.xml-to-umm-mappings.dsl :refer :all]
            [cmr.umm-spec.xml-to-umm-mappings.add-parse-type :as apt]
            [cmr.umm-spec.json-schema :as js]
            [cmr.umm-spec.simple-xpath :as xp]
            [cmr.umm-spec.xml-to-umm-mappings.parser :as parser]))

(defn- parse-version
  "Returns a UMM Version value parsed from a DIF 10 element context."
  [xpath-context]
  (let [val (-> xpath-context :context first :content first (cx/string-at-path [:Version]))]
    (when-not (= val "Not provided")
      val)))

(defn- parse-platform-type
  "Returns a UMM Platform Type value parsed from a DIF 10 Platform element context."
  [xpath-context]
  (let [val (-> xpath-context :context first (cx/string-at-path [:Type]))]
    (when (not= val "Not provided")
      val)))

(def characteristic-parser
  (matching-object :Name :Description :DataType :Unit :Value))

(def dif10-xml-to-umm-c
  (apt/add-parsing-types
    js/umm-c-schema
    (object
      {:EntryTitle (xpath "/DIF/Entry_Title")
       :EntryId (xpath "/DIF/Entry_ID")
       :Version parse-version
       :Abstract (xpath "/DIF/Summary/Abstract")
       :CollectionDataType (xpath "/DIF/Collection_Data_Type")
       :Purpose (xpath "/DIF/Summary/Purpose")
       :DataLanguage (xpath "/DIF/Dataset_Language")
       :TemporalKeywords (select "/DIF/Temporal_Coverage/Temporal_Info/Ancillary_Temporal_Keyword")
       :CollectionProgress (xpath "/DIF/Data_Set_Progress")
       :Quality (xpath "/DIF/Quality")
       :AccessConstraints (object
                            {:Description (xpath "/DIF/Access_Constraints")})
       :UseConstraints (xpath "/DIF/Use_Constraints")
       :Platforms (for-each "/DIF/Platform"
                    (object
                     {:ShortName (xpath "Short_Name")
                      :LongName (xpath "Long_Name")
                      :Type parse-platform-type
                      :Characteristics (for-each "Characteristics" characteristic-parser)
                      :Instruments (for-each "Instrument"
                                     (object
                                      {:ShortName (xpath "Short_Name")
                                       :LongName (xpath "Long_Name")
                                       :Technique (xpath "Technique")
                                       :NumberOfSensors (xpath "NumberOfSensors")
                                       :Characteristics (for-each "Characteristics"
                                                          characteristic-parser)
                                       :OperationalModes (select "OperationalMode")
                                       :Sensors (for-each "Sensor"
                                                  (object
                                                   {:ShortName (xpath "Short_Name")
                                                    :LongName (xpath "Long_Name")
                                                    :Technique (xpath "Technique")
                                                    :Characteristics (for-each "Characteristics"
                                                                       characteristic-parser)}))}))}))
       :TemporalExtents (for-each "/DIF/Temporal_Coverage"
                          (object
                            {:TemporalRangeType (xpath "Temporal_Range_Type")
                             :PrecisionOfSeconds (xpath "Precision_Of_Seconds")
                             :EndsAtPresentFlag (xpath "Ends_At_Present_Flag")
                             :RangeDateTimes (for-each "Range_DateTime"
                                               (object
                                                 {:BeginningDateTime (xpath "Beginning_Date_Time")
                                                  :EndingDateTime (xpath "Ending_Date_Time")}))
                             :SingleDateTimes (select "Single_DateTime")
                             :PeriodicDateTimes (for-each "Periodic_DateTime"
                                                  (object
                                                    {:Name (xpath "Name")
                                                     :StartDate (xpath "Start_Date")
                                                     :EndDate (xpath "End_Date")
                                                     :DurationUnit (xpath "Duration_Unit")
                                                     :DurationValue (xpath "Duration_Value")
                                                     :PeriodCycleDurationUnit (xpath "Period_Cycle_Duration_Unit")
                                                     :PeriodCycleDurationValue (xpath "Period_Cycle_Duration_Value")}))}))
       :ProcessingLevel (object
                          {:Id (xpath "/DIF/Product_Level_Id")})
       :AdditionalAttributes
       (for-each "/DIF/AdditionalAttributes"
                 (matching-object :Name :Description :DataType :ParameterRangeBegin :ParameterRangeEnd
                                  :Value :MeasurementResolution :ParameterUnitsOfMeasure
                                  :ParameterValueAccuracy :ValueAccuracyExplanation))})))
