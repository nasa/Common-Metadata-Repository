(ns cmr.umm.dif10.collection.temporal
  "Contains functions for parsing the DIF10 temporal coverage element."
  (:require [cmr.common.xml :as cx]
            [cmr.common.date-time-parser :as parser]
            [cmr.umm.collection :as c]))

(defn- xml-elem->RangeDateTimes
  "Returns a list of UMM RangeDateTimes from a parsed Temporal XML structure"
  [temporal-element]
  (let [elements (cx/elements-at-path temporal-element [:Range_DateTime])]
    (map #(c/map->RangeDateTime {:beginning-date-time (cx/datetime-at-path % [:Beginning_Date_Time])
                                 :ending-date-time (cx/datetime-at-path % [:Ending_Date_Time])})
         elements)))

(defn- xml-elem->PeriodicDateTimes
  "Returns a list of UMM PeriodicDateTimes from a parsed Temporal XML structure"
  [temporal-element]
  (let [elements (cx/elements-at-path temporal-element [:Periodic_DateTime])]
    (map #(c/map->PeriodicDateTime {:name (cx/string-at-path % [:Name])
                                    :start-date (cx/datetime-at-path % [:Start_Date])
                                    :end-date (cx/datetime-at-path % [:End_Date])
                                    :duration-unit (cx/string-at-path % [:Duration_Unit])
                                    :duration-value (cx/long-at-path % [:Duration_Value])
                                    :period-cycle-duration-unit (cx/string-at-path % [:Period_Cycle_Duration_Unit])
                                    :period-cycle-duration-value (cx/long-at-path % [:Period_Cycle_Duration_Value])})
         elements)))

(defn string->datetime
  "convert the string to joda datetime if it is in either DateTime or Date format."
  [datetime-string]
  (when datetime-string
    (if (re-matches #"^\d\d\d\d-\d?\d-\d?\d$" datetime-string)
      (parser/parse-date datetime-string)
      (parser/parse-datetime datetime-string))))

(defn xml-elem->Temporal
  "Returns a UMM Temporal from a parsed Collection Content XML structure"
  [collection-element]
  (when-let [temporal-element (cx/element-at-path collection-element [:Temporal_Coverage])]
    (let [range-date-times (xml-elem->RangeDateTimes temporal-element)
          periodic-date-times (xml-elem->PeriodicDateTimes temporal-element)]
      (c/map->Temporal {:time-type (cx/string-at-path temporal-element [:Time_Type])
                        :date-type (cx/string-at-path temporal-element [:Date_Type])
                        :temporal-range-type (cx/string-at-path temporal-element [:Temporal_Range_Type])
                        :precision-of-seconds (cx/long-at-path temporal-element [:Precision_Of_Seconds])
                        :ends-at-present-flag (cx/bool-at-path temporal-element [:Ends_At_Present_Flag])
                        :range-date-times range-date-times
                        :single-date-times (cx/datetimes-at-path temporal-element [:Single_Date_Time])
                        :periodic-date-times periodic-date-times}))))