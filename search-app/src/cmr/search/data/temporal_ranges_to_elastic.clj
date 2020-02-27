(ns cmr.search.data.temporal-ranges-to-elastic
  "Functions to convert temporal range search params into an elastic sort script to sort by
  temporal overlap for temporal relevancy"
 (:require
  [clj-time.coerce :as time-coerce]
  [clj-time.core :as time]
  [clojure.java.io :as io]
  [cmr.common.util :as util]
  [cmr.search.services.query-execution.temporal-conditions-results-feature :as temporal-conditions]))

(def default-temporal-start-date-time
  "The default date-time to use for relevancy calculations when one is not specified in the
  temporal range. Use an early date to get all collections."
  (time-coerce/to-long (time/date-time 1900 1 1)))

(defn temporal-range->elastic-param
  "Convert a temporal range to the right format for the elastic script. Change the dates to longs, populate
   the start/end dates with defaults as needed, and change the keys to snake case. Do whatever
   processing can be done here rather than the script for performance considerations.
   Input is a map of {:start-date :end-date} with one of those possibly nil.

   Will return:
   {:start_date start-date or default}
    :end_date end-date or today
    :range adjusted end-date - adjusted start-date"
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
  "Painless script used by elastic to calculate temporal overlap based on the collection's indexed
  start and end date and the temporal ranges specified as search params. The temporal overlap is
  the percentage of the ranges covered by the collection.
  For each temporal range search param, calculate span of time that the collection overlaps. Add
  all of the spans and divide by the sum of the time spans in the temporal range search params."
  (slurp (io/resource "temporal_overlap.painless")))

(defn temporal-overlap-sort-script
  "Create the script to sort by temporal overlap percent in descending order. Get the temporal ranges
  from the query, format them for elastic, and send them as params for the script."
  [query]
  (let [temporal-ranges (temporal-conditions/get-query-temporal-conditions query)
        temporal-ranges (map temporal-range->elastic-param temporal-ranges)]
    {:type :number
     :script {:source script
              :params {:temporalRanges temporal-ranges
                       :rangeSpan (apply + (map :range temporal-ranges))}}
     :order :desc}))
