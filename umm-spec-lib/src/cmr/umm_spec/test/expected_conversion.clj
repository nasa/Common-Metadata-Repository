(ns cmr.umm-spec.test.expected-conversion
  "This contains functions for manipulating the expected UMM record when taking a UMM record
  writing it to an XML format and parsing it back. Conversion from a UMM record into metadata
  can be lossy if some fields are not supported by that format"
  (:require [cmr.umm-spec.models.common :as cmn]))

(defmulti ^:private convert-internal
  "Returns UMM collection that would be expected when converting the source UMM-C record into the
  destination XML format and parsing it back to a UMM-C record."
  (fn [umm-coll metadata-format]
    metadata-format))

(defmethod convert-internal :default
  [umm-coll _]
  umm-coll)

;;; Utililty Functions

(defn update-in-each
  "Like update-in but applied to each value in seq at path."
  [m path f & args]
  (update-in m path (fn [xs]
                      (map (fn [x]
                             (apply f x args))
                           xs))))

(defn single-date->range
  "Returns a RangeDateTimeType for a single date."
  [date]
  (cmn/map->RangeDateTimeType {:BeginningDateTime date
                               :EndingDateTime    date}))

(defn single-dates->ranges
  "Returns a TemporalExtentType with any SingleDateTime values mapped
  to be RangeDateTime values."
  [temporal]
  (let [singles (:SingleDateTimes temporal)]
    (if (not (empty? singles))
      (-> temporal
          (assoc :SingleDateTimes nil)
          (assoc :RangeDateTimes (map single-date->range singles)))
      temporal)))

(defn split-temporals
  "Returns a seq of temporal extents with a new extent for each value under key
  k (e.g. :RangeDateTimes) in each source temporal extent."
  [k temporal-extents]
  (reduce (fn [result extent]
            (if-let [values (get extent k)]
              (concat result (map #(cmn/map->TemporalExtentType {k [%]})
                                  values))
              (concat result [extent])))
          []
          temporal-extents))

;;; Format-Specific Translation Functions

;; ECHO 10

(defmethod convert-internal :echo10
  [umm-coll _]
  (-> umm-coll
      (update-in [:TemporalExtents] (partial take 1))
      (assoc :DataLanguage nil)))

;; DIF 9

(defn dif-temporal
  "Returns the expected value of a parsed DIF 9 UMM record's :TemporalExtents."
  [temporal-extents]
  (->> temporal-extents
       ;; Periodic temporal extents are not supported in DIF 9, so we
       ;; must remove them.
       (remove :PeriodicDateTimes)
       ;; Only ranges are supported by DIF 9, so we need to convert
       ;; single dates to range types.
       (map single-dates->ranges)
       (map #(assoc %
                    :TemporalRangeType nil
                    :PrecisionOfSeconds nil
                    :EndsAtPresentFlag nil))))

(defmethod convert-internal :dif
  [umm-coll _]
  (update-in umm-coll [:TemporalExtents] dif-temporal))

;; ISO 19115-2

(defn expected-iso-19115-2-temporal
  [temporal-extents]
  (->> temporal-extents
       (map #(assoc %
                    :TemporalRangeType nil
                    :PrecisionOfSeconds nil
                    :EndsAtPresentFlag nil))
       (remove :PeriodicDateTimes)
       (split-temporals :RangeDateTimes)
       (split-temporals :SingleDateTimes)
       (map cmn/map->TemporalExtentType)
       seq))

(defmethod convert-internal :iso19115
  [umm-coll _]
  (update-in umm-coll [:TemporalExtents] expected-iso-19115-2-temporal))

(defmethod convert-internal :iso-smap
  [umm-coll _]
  (convert-internal umm-coll :iso19115))

;;; Unimplemented Fields

(def not-implemented-fields
  "This is a list of required but not implemented fields."
  #{:Platforms :ProcessingLevel :RelatedUrls :DataDates :ResponsibleOrganizations :ScienceKeywords
    :SpatialExtent})

(defn- dissoc-not-implemented-fields
  "Removes not implemented fields since they can't be used for comparison"
  [record]
  (reduce (fn [r field]
            (assoc r field nil))
          record
          not-implemented-fields))

;;; Public API

(defn convert
  "Returns input UMM-C record transformed according to the specified transformation for
  metadata-format."
  [umm-coll metadata-format]
  (if (= metadata-format :umm-json)
    umm-coll
    (dissoc-not-implemented-fields
      (convert-internal umm-coll metadata-format))))
