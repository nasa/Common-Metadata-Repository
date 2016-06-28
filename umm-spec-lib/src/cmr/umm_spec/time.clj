(ns cmr.umm-spec.time
  "Functions for working with the variety of temporal extent
  structures in the UMM model."
  (:require [clj-time.core :as t]))

(defn temporal-all-dates
  "Returns the set of all dates contained in the given TemporalExtent record."
  [temporal]
  (let [ranges  (:RangeDateTimes temporal)
        singles (:SingleDateTimes temporal)
        periods (:PeriodicDateTimes temporal)]
    (set
     (concat singles
             (map :BeginningDateTime ranges)
             (map :EndingDateTime ranges)
             (map :StartDate periods)
             (map :EndDate periods)))))

(defn collection-start-date
  "Returns the earliest date found in the temporal extent of a UMM collection. Nil indicates the
   collection has no temporal extents."
  [umm-coll]
  (when-let [dates (seq (remove nil? (mapcat temporal-all-dates (:TemporalExtents umm-coll))))]
    (t/earliest dates)))

(defn collection-end-date
  "Returns the latest date found in the temporal extent of a UMM collection. The keyword :present
   indicates the collection does not have an end date and continues to the present."
  [umm-coll]
  ;; TODO make ends at present flag work
  (let [date-set (mapcat temporal-all-dates (:TemporalExtents umm-coll))]
    (when (seq date-set)
      (if (contains? date-set nil)
        :present
        (t/latest dates)))))

(comment
 (->> (get (proto/saved-values) "(proto/save 5)")
      (map #(get % 'umm-coll))
      (map collection-start-date)))
