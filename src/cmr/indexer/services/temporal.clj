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

(defmulti start-date
  "Returns start-date of the temporal coverage"
  (fn [concept-type temporal-coverage]
    concept-type))

(defmulti end-date
  "Returns end-date of the temporal coverage"
  (fn [concept-type temporal-coverage]
    concept-type))

(defmethod start-date :collection
  [concept-type temporal-coverage]
  (range-start-date (temporal-coverage->range-date-times temporal-coverage)))

(defmethod end-date :collection
  [concept-type temporal-coverage]
  ;; Return nil if ends-at-present-flag is true, otherwise returns the latest end_date_time of all the given range_date_times
  (when-not (:ends-at-present-flag temporal-coverage)
    (range-end-date (temporal-coverage->range-date-times temporal-coverage))))

(defmethod start-date :granule
  [concept-type temporal-coverage]
  (let [{:keys [range-date-time single-date-time]} temporal-coverage]
    (if single-date-time
      single-date-time
      (when range-date-time (:beginning-date-time range-date-time)))))

(defmethod end-date :granule
  [concept-type temporal-coverage]
  (let [{:keys [range-date-time single-date-time]} temporal-coverage]
    (if single-date-time
      single-date-time
      (when range-date-time (:ending-date-time range-date-time)))))
