(ns cmr.umm-spec.time
  "Functions for working with the variety of temporal extent
  structures in the UMM model."
  (:require
    [clj-time.core :as t]
    [cmr.umm-spec.date-util :as date-util]))

(defn temporal-all-dates
  "Returns the set of all dates contained in the given TemporalExtent record. :present is used to
   indicate the temporal range goes to the present date."
  [temporal]
  (let [ranges  (:RangeDateTimes temporal)
        singles (:SingleDateTimes temporal)
        periods (:PeriodicDateTimes temporal)]
    (set
     (concat singles
             (when (:EndsAtPresentFlag temporal)
               [:present])
             (map :BeginningDateTime ranges)
             ;; ending date time is optional. If it's not included it ends at present.
             (map #(or (get % :EndingDateTime) :present) ranges)
             (map :StartDate periods)
             ;; end date is required for periodic
             (map :EndDate periods)))))

(defn collection-start-date
  "Returns the earliest date found in the temporal extent of a UMM collection. Nil indicates the
   collection has no temporal extents."
  [umm-coll]
  (when-let [dates (seq (remove #(= :present %) (mapcat temporal-all-dates (:TemporalExtents umm-coll))))]
    (t/earliest dates)))

(defn collection-end-date
  "Returns the latest date found in the temporal extent of a UMM collection. The keyword :present
   indicates the collection does not have an end date and continues to the present. Nil indicates the
   collection has no temporal extents."
  [umm-coll]
  (let [date-set (reduce into #{} (map temporal-all-dates (:TemporalExtents umm-coll)))]
    (when (seq date-set)
      (if (contains? date-set :present)
        :present
        (t/latest date-set)))))

(defn normailized-end-date
  "Returns the normalized end date of the collection by changing the :present end date to nil
   to facilitate the handling of :present end date during ingest and indexing."
  [umm-coll]
  (let [end-date (collection-end-date umm-coll)]
    (when-not (= :present end-date) end-date)))
