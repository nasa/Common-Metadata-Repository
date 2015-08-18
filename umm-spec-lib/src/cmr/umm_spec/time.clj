(ns cmr.umm-spec.time
  "Functions for working with the variety of temporal extent
  structures in the UMM model."
  (:require [clj-time.core :as t]))

(defn temporal-all-dates
  "Returns the set of all dates contained in the given TemporalExtent record."
  [temporal]
  (let [ranges  (:RangeDateTime temporal)
        singles (:SingleDateTime temporal)
        periods (:PeriodicDateTime temporal)]
    (set
     (concat singles
             (map :BeginningDateTime ranges)
             (map :EndingDateTime ranges)
             (map :StartDate periods)
             (map :EndDate periods)))))

(defn collection-start-date
  "Returns the earliest date found in the temporal extent of a UMM
  collection."
  [umm-coll]
  (t/earliest (mapcat temporal-all-dates (:TemporalExtent umm-coll))))

(defn collection-end-date
  "Returns the latest date found in the temporal extent of a UMM
  collection."
  [umm-coll]
  (t/latest (mapcat temporal-all-dates (:TemporalExtent umm-coll))))
