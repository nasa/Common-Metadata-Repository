(ns cmr.search.data.temporal-ranges-to-elastic
  "Functions to "
 (:require
  [clj-time.coerce :as time-coerce]
  [clj-time.core :as time]
  [cmr.common.util :as util]
  [cmr.search.services.query-walkers.temporal-range-extractor :as temporal-range-extractor]))

(def default-temporal-start-date-time
  "The default date-time to use for relevancy calculations when one is not specified in the temporal range"
  (time-coerce/to-long (time/date-time 1970 1 1)))

(defn temporal-range->elastic-param
  "Convert a temporal range to the right format for the elastic script. Change the dates to longs, populate
   the start/end dates with defaults as needed, and change the keys to snake case. Do whatever
   processing can be done here rather than the script for performance considerations."
  [temporal-range]
  (let [{:keys [start-date end-date]} temporal-range
        temporal-range {:start-date (if start-date
                                      (time-coerce/to-long start-date)
                                      default-temporal-start-date-time)
                        :end-date (if end-date
                                    (time-coerce/to-long end-date)
                                    (time-coerce/to-long (time/today)))}
        temporal-range (assoc temporal-range :range (- (:end-date temporal-range) (:start-date temporal-range)))]
    (util/map-keys->snake_case temporal-range)))

(def script
  "def totalOverlap = 0;
   for (range in temporalRanges)
   {
     def overlapStartDate = range.start_date;
     if (doc['start-date'].value != 0 && doc['start-date'].value > overlapStartDate)
      { overlapStartDate = doc['start-date'].value; }
     def overlapEndDate = range.end_date;
     if (doc['end-date'].value != 0 && doc['end-date'].value < overlapEndDate)
      { overlapEndDate = doc['end-date'].value; }
     if (overlapEndDate > overlapStartDate)
      { totalOverlap += overlapEndDate - overlapStartDate; }
   }
   if (rangeSpan > 0) { totalOverlap / rangeSpan; }
   else { 0; }")

(defn temporal-overlap-sort-script
 "Create the script to sort by temporal overlap percent in descending order. Get the temporal ranges
  from the query, format them for elastic, and send them as params for the script."
 [query]
 (let [temporal-ranges (temporal-range-extractor/extract-temporal-ranges query)
       temporal-ranges (map temporal-range->elastic-param temporal-ranges)]
   {:script script
    :type :number
    :params {:temporalRanges temporal-ranges
             :rangeSpan (apply + (map :range temporal-ranges))}
    :order :desc}))
