(ns cmr.indexer.services.temporal
  "Contains functions to convert UMM temporal-coverage structure to parts needed for indexing"
  (:require [clj-time.core :as t]
            [cmr.umm.collection :as c]))

(defn- single-date-times->range-date-times
  "Convert a list of single date times to a list of range date times"
  [single-date-times]
  (map
    #(c/map->RangeDateTime {:beginning-date-time % :ending-date-time %})
    single-date-times))

(defn- periodic-date-times->range-date-times
  "Convert a list of periodic date times to a list of range date times"
  [periodic-date-times]
  (map
    #(c/map->RangeDateTime {:beginning-date-time (:start-date %) :ending-date-time (:end-date %)})
    periodic-date-times))

(defn- temporal-coverage->range-date-times
  "Convert temporal coverage to a list of range date times"
  [temporal-coverage]
  (let [{:keys [range-date-times single-date-times periodic-date-times]} temporal-coverage]
    (concat range-date-times
            (single-date-times->range-date-times single-date-times)
            (periodic-date-times->range-date-times periodic-date-times))))

(defn- range-start-date
  "Returns the earliest start-date of the list of range date times"
  [range-date-times]
  (->> range-date-times
       (map #(:beginning-date-time %))
       (remove nil?)
       (sort t/after?)
       last))

(defn- range-end-date
  "Returns the latest end-date of the list of range date times"
  [range-date-times]
  (->> range-date-times
       (map #(:ending-date-time %))
       (remove nil?)
       (sort t/after?)
       first))

(defn start-date
  "Returns the start-date of the given temporal coverage"
  [temporal-coverage]
  (range-start-date (temporal-coverage->range-date-times temporal-coverage)))

(defn end-date
  "Returns the end-date of the given temporal coverage"
  [temporal-coverage]
  ;; Return nil if ends-at-present-flag is true, otherwise returns the latest end_date_time of all the given range_date_times
  (when-not (:ends-at-present-flag temporal-coverage)
    (range-end-date (temporal-coverage->range-date-times temporal-coverage))))
