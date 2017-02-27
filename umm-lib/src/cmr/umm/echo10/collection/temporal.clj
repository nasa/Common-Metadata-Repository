(ns cmr.umm.echo10.collection.temporal
  "Contains functions for parsing and generating the ECHO10 dialect."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.generator-util :as gu]))

(defn- xml-elem->RangeDateTimes
  "Returns a list of UMM RangeDateTimes from a parsed Temporal XML structure"
  [temporal-element]
  (let [elements (cx/elements-at-path temporal-element [:RangeDateTime])]
    (map #(c/map->RangeDateTime {:beginning-date-time (cx/datetime-at-path % [:BeginningDateTime])
                                 :ending-date-time (cx/datetime-at-path % [:EndingDateTime])})
         elements)))

(defn- xml-elem->PeriodicDateTimes
  "Returns a list of UMM PeriodicDateTimes from a parsed Temporal XML structure"
  [temporal-element]
  (let [elements (cx/elements-at-path temporal-element [:PeriodicDateTime])]
    (map #(c/map->PeriodicDateTime {:name (cx/string-at-path % [:Name])
                                    :start-date (cx/datetime-at-path % [:StartDate])
                                    :end-date (cx/datetime-at-path % [:EndDate])
                                    :duration-unit (cx/string-at-path % [:DurationUnit])
                                    :duration-value (cx/long-at-path % [:DurationValue])
                                    :period-cycle-duration-unit (cx/string-at-path % [:PeriodCycleDurationUnit])
                                    :period-cycle-duration-value (cx/long-at-path % [:PeriodCycleDurationValue])})
         elements)))

(defn xml-elem->Temporal
  "Returns a UMM Temporal from a parsed Collection Content XML structure"
  [collection-element]
  (when-let [temporal-element (cx/element-at-path collection-element [:Temporal])]
    (let [range-date-times (xml-elem->RangeDateTimes temporal-element)
          periodic-date-times (xml-elem->PeriodicDateTimes temporal-element)]
      (c/map->Temporal {:time-type (cx/string-at-path temporal-element [:TimeType])
                        :date-type (cx/string-at-path temporal-element [:DateType])
                        :temporal-range-type (cx/string-at-path temporal-element [:TemporalRangeType])
                        :precision-of-seconds (cx/long-at-path temporal-element [:PrecisionOfSeconds])
                        :ends-at-present-flag (cx/bool-at-path temporal-element [:EndsAtPresentFlag])
                        :range-date-times range-date-times
                        :single-date-times (cx/datetimes-at-path temporal-element [:SingleDateTime])
                        :periodic-date-times periodic-date-times}))))

(defn generate-temporal
  "Generates the temporal element of ECHO10 XML from a UMM Collection temporal record."
  [temporal]
  (when temporal
    (let [{:keys [time-type date-type temporal-range-type precision-of-seconds
                  ends-at-present-flag range-date-times single-date-times periodic-date-times]} temporal]
      (x/element :Temporal {}
                 (gu/optional-elem :TimeType time-type)
                 (gu/optional-elem :DateType date-type)
                 (gu/optional-elem :TemporalRangeType temporal-range-type)
                 (gu/optional-elem :PrecisionOfSeconds precision-of-seconds)
                 (gu/optional-elem :EndsAtPresentFlag ends-at-present-flag)

                 (for [range-date-time range-date-times]
                   (let [{:keys [beginning-date-time ending-date-time]} range-date-time]
                     (x/element :RangeDateTime {}
                                (when beginning-date-time
                                  (x/element :BeginningDateTime {} (str beginning-date-time)))
                                (when ending-date-time
                                  (x/element :EndingDateTime {} (str ending-date-time))))))

                 (for [single-date-time single-date-times]
                   (x/element :SingleDateTime {} (str single-date-time)))

                 (for [periodic-date-time periodic-date-times]
                   (let [{:keys [name start-date end-date duration-unit duration-value
                                 period-cycle-duration-unit period-cycle-duration-value]} periodic-date-time]
                     (x/element :PeriodicDateTime {}
                                (gu/optional-elem :Name name)
                                (when start-date (x/element :StartDate {} (str start-date)))
                                (when end-date (x/element :EndDate {} (str end-date)))
                                (gu/optional-elem :DurationUnit duration-unit)
                                (gu/optional-elem :DurationValue duration-value)
                                (gu/optional-elem :PeriodCycleDurationUnit period-cycle-duration-unit)
                                (gu/optional-elem :PeriodCycleDurationValue period-cycle-duration-value))))))))
