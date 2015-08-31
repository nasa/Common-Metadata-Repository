(ns cmr.umm-spec.xml-to-umm-mappings.dif10
  "Defines mappings from DIF10 XML into UMM records"
  (:require [cmr.common.xml :as cx]
            [cmr.umm-spec.xml-to-umm-mappings.dsl :refer :all]
            [cmr.umm-spec.xml-to-umm-mappings.add-parse-type :as apt]
            [cmr.umm-spec.json-schema :as js]
            [cmr.umm-spec.simple-xpath :as xp]))

(defn- parse-platform-type
  "Returns a UMM Platform Type value parsed from a DIF 10 Platform element context."
  [xpath-context]
  (let [val (-> xpath-context :context first (cx/string-at-path [:Type]))]
    (when (not= val "Not provided")
      val)))

(def dif10-xml-to-umm-c
  (apt/add-parsing-types
    js/umm-c-schema
    (object
      {:EntryTitle (xpath "/DIF/Entry_Title")
       :EntryId (xpath "/DIF/Entry_ID")
       :Version (xpath "/DIF/Version")
       :Abstract (xpath "/DIF/Summary/Abstract")
       :Purpose (xpath "/DIF/Summary/Purpose")
       :DataLanguage (xpath "/DIF/Dataset_Language")
       :Quality (xpath "/DIF/Quality")
       :AccessConstraints (object
                            {:Description (xpath "/DIF/Access_Constraints")})
       :UseConstraints (xpath "/DIF/Use_Constraints")
       :Platforms (for-each "/DIF/Platform"
                    (object
                     {:ShortName (xpath "Short_Name")
                      :LongName (xpath "Long_Name")
                      :Type parse-platform-type
                      :Characteristics (for-each "Characteristics"
                                         (matching-object :Name :Description :DataType :Unit :Value))}))
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
                                                     :PeriodCycleDurationValue (xpath "Period_Cycle_Duration_Value")}))}))})))
