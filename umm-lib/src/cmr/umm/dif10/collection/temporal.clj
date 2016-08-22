(ns cmr.umm.dif10.collection.temporal
  "Contains functions for parsing the DIF10 temporal coverage element."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.common.date-time-parser :as parser]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.generator-util :as gu]
            [cmr.umm.dif.date-util :as date-util]))

(defn- xml-elem->RangeDateTimes
  "Returns a list of UMM RangeDateTimes from a parsed Temporal XML structure"
  [temporal-element]
  (let [elements (cx/elements-at-path temporal-element [:Range_DateTime])]
    (map #(c/map->RangeDateTime {:beginning-date-time (cx/datetime-at-path % [:Beginning_Date_Time])
                                 :ending-date-time (date-util/parse-dif-end-date
                                                     (cx/string-at-path % [:Ending_Date_Time]))})
         elements)))

(defn- xml-elem->PeriodicDateTimes
  "Returns a list of UMM PeriodicDateTimes from a parsed Temporal XML structure"
  [temporal-element]
  (let [elements (cx/elements-at-path temporal-element [:Periodic_DateTime])]
    (map (fn [element]
           (c/map->PeriodicDateTime
             {:name (cx/string-at-path element [:Name])
              :start-date (cx/datetime-at-path element [:Start_Date])
              :end-date (date-util/parse-dif-end-date (cx/string-at-path element [:End_Date]))
              :duration-unit (cx/string-at-path element [:Duration_Unit])
              :duration-value (cx/long-at-path element [:Duration_Value])
              :period-cycle-duration-unit (cx/string-at-path element [:Period_Cycle_Duration_Unit])
              :period-cycle-duration-value (cx/long-at-path element [:Period_Cycle_Duration_Value])}))
         elements)))

(defn string->datetime
  "convert the string to joda datetime if it is in either DateTime or Date format."
  [datetime-string]
  (when datetime-string
    (parser/parse-datetime datetime-string)))

(defn xml-elem->Temporal
  "Returns a UMM Temporal from a parsed Collection Content XML structure"
  [collection-element]
  (when-let [temporal-element (cx/element-at-path collection-element [:Temporal_Coverage])]
    (c/map->Temporal
      {:time-type (cx/string-at-path temporal-element [:Time_Type])
       :date-type (cx/string-at-path temporal-element [:Date_Type])
       :temporal-range-type (cx/string-at-path temporal-element [:Temporal_Range_Type])
       :precision-of-seconds (cx/long-at-path temporal-element [:Precision_Of_Seconds])
       :ends-at-present-flag (cx/bool-at-path temporal-element [:Ends_At_Present_Flag])
       :range-date-times (xml-elem->RangeDateTimes temporal-element)
       :single-date-times (cx/datetimes-at-path temporal-element [:Single_DateTime])
       :periodic-date-times (xml-elem->PeriodicDateTimes temporal-element)})))

(defn generate-temporal
  "Generates the temporal element of ECHO10 XML from a UMM Collection temporal record."
  [temporal]
  (when temporal
    (let [{:keys [time-type date-type temporal-range-type precision-of-seconds
                  ends-at-present-flag range-date-times single-date-times periodic-date-times]} temporal]
      (x/element :Temporal_Coverage {}
                 (gu/optional-elem :Time_Type time-type)
                 (gu/optional-elem :Date_Type date-type)
                 (gu/optional-elem :Temporal_Range_Type temporal-range-type)
                 (gu/optional-elem :Precision_Of_Seconds precision-of-seconds)
                 (gu/optional-elem :Ends_At_Present_Flag ends-at-present-flag)

                 (for [range-date-time range-date-times]
                   (let [{:keys [beginning-date-time ending-date-time]} range-date-time]
                     (x/element :Range_DateTime {}
                                (when beginning-date-time
                                  (x/element :Beginning_Date_Time {} (str beginning-date-time)))
                                (when ending-date-time
                                  (x/element :Ending_Date_Time {} (str ending-date-time))))))

                 (for [single-date-time single-date-times]
                   (x/element :Single_DateTime {} (str single-date-time)))

                 (for [{:keys [name start-date end-date duration-unit duration-value
                               period-cycle-duration-unit period-cycle-duration-value]} periodic-date-times]
                   (x/element :Periodic_DateTime {}
                              (gu/optional-elem :Name name)
                              (when start-date (x/element :Start_Date {} (str start-date)))
                              (when end-date (x/element :End_Date {} (str end-date)))
                              (gu/optional-elem :Duration_Unit duration-unit)
                              (gu/optional-elem :Duration_Value duration-value)
                              (gu/optional-elem :Period_Cycle_Duration_Unit period-cycle-duration-unit)
                              (gu/optional-elem :Period_Cycle_Duration_Value period-cycle-duration-value)))))))
