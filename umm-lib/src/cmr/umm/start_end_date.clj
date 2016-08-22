(ns cmr.umm.start-end-date
  "Contains functions to convert UMM temporal structure to start and end dates."
  (:require [clj-time.core :as t]
            [cmr.umm.umm-collection :as c]))

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

(defn- temporal->range-date-times
  "Convert temporal coverage to a list of range date times"
  [temporal]
  (let [{:keys [range-date-times single-date-times periodic-date-times]} temporal]
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
  (let [ending-dates (map #(:ending-date-time %) range-date-times)]
    (when-not (some nil? ending-dates)
    ;; If some are nil, we will return nil instead of an end-date to indicate an open range.
      (first (sort t/after? ending-dates)))))

(defmulti start-date
  "Returns start-date of the temporal coverage"
  (fn [concept-type temporal]
    concept-type))

(defmulti end-date
  "Returns end-date of the temporal coverage"
  (fn [concept-type temporal]
    concept-type))

(defmethod start-date :collection
  [concept-type temporal]
  (range-start-date (temporal->range-date-times temporal)))

(defmethod end-date :collection
  [concept-type temporal]
  ;; Return nil if ends-at-present-flag is true, otherwise returns the latest end_date_time of all the given range_date_times
  (when-not (:ends-at-present-flag temporal)
    (range-end-date (temporal->range-date-times temporal))))

(defmethod start-date :granule
  [concept-type temporal]
  (let [{:keys [range-date-time single-date-time]} temporal]
    (if single-date-time
      single-date-time
      (when range-date-time (:beginning-date-time range-date-time)))))

(defmethod end-date :granule
  [concept-type temporal]
  (let [{:keys [range-date-time single-date-time]} temporal]
    (if single-date-time
      single-date-time
      (when range-date-time (:ending-date-time range-date-time)))))
