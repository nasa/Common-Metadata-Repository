(ns cmr.umm-spec.time
  "Functions for working with the variety of temporal extent
  structures in the UMM model."
  (:require
    [clj-time.core :as t]))

(defn- normalize-temporal-ranges
  "When ends at present flag is true, make sure there is a temporal range with
  a nil end date. If not, find the last end date and set to nil"
  [temporal-ranges ends-at-present]
  (let [date-set (seq (map :EndingDateTime temporal-ranges))]
    (if (and ends-at-present date-set (not (contains? (set date-set) nil)))
      (let [latest-date (t/latest date-set)]
        (map #(if (= (:EndingDateTime %) latest-date)
                (assoc % :EndingDateTime nil)
                %)
              temporal-ranges))
      (map #(if (nil? (:EndingDateTime %)) (assoc % :EndingDateTime nil) %) temporal-ranges))))

(defn- latest-or-nil
  "Return the latest of the 2 dates or nil if at least one is nil"
  [date1 date2]
  (when (and (some? date1) (some? date2))
    (t/latest date1 date2)))

(defn- resolve-overlap
  "Check if date range overlaps previous range (ranges should be sorted). If
  so, return the list with the range and previous range merged together"
  [ranges range]
  (if-let [prev-range (last ranges)]
    (if (or (nil? (:EndingDateTime prev-range))
            (t/after? (:EndingDateTime prev-range) (:BeginningDateTime range)))
      (conj (butlast ranges) {:BeginningDateTime (:BeginningDateTime prev-range)
                              :EndingDateTime (latest-or-nil (:EndingDateTime prev-range) (:EndingDateTime range))})
      (conj ranges range))
    [range]))

(defn- resolve-range-overlaps
  "Merge ranges so that there are no overlaps - i.e. 2000-2003 and 2002-2005 would
  become 2000-2005"
  [ranges]
  (let [ranges (sort-by :BeginningDateTime ranges)]
    (reduce resolve-overlap [] ranges)))

(defn temporal-ranges
  "Returns all the :RangeDateTimes and :SingeDateTimes contained in the given TemporalExtent record,
   as a list of {:BeginningDateTime xxx :EndingDateTime xxx} with no overlaps"
  [temporal]
  (let [ranges (:RangeDateTimes temporal)
        normalized-ranges (normalize-temporal-ranges ranges (:EndsAtPresentFlag temporal))
        singles (:SingleDateTimes temporal)
        ;; Treat the singles as ranges with the same :BegingDateTime and :EndingDateTime.
        single-ranges (map #(zipmap [:BeginningDateTime :EndingDateTime] (repeat %)) singles)]
     (resolve-range-overlaps (concat normalized-ranges single-ranges))))

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
  (when-let [dates (seq (remove #(or (nil? %) (= :present %)) (mapcat temporal-all-dates (:TemporalExtents umm-coll))))]
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

(defn normalized-end-date
  "Returns the normalized end date of the collection by changing the :present end date to nil
   to facilitate the handling of :present end date during ingest and indexing."
  [umm-coll]
  (let [end-date (collection-end-date umm-coll)]
    (when-not (= :present end-date) end-date)))
