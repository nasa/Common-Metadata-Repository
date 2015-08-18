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
  (let [singles (:SingleDateTime temporal)]
    (if (not (empty? singles))
      (-> temporal
          (dissoc :SingleDateTime)
          (assoc :RangeDateTime (map single-date->range singles)))
      temporal)))

(defn merge-ranges
  "Returns t1 with :RangeDateTime concatenated together with t2's."
  [t1 t2]
  (update-in t1 [:RangeDateTime] concat (:RangeDateTime t2)))

(defn split-temporals
  "Returns a seq of temporal extents with a new extent for each value under key
  k (e.g. :RangeDateTime) in each source temporal extent."
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
  ;; ECHO10 returns entry id as a combination of short name and version. It generates short name
  ;; from entry id. So the expected entry id when going from umm->echo10->umm is the original
  ;; entry id concatenated with the version id.
  (-> umm-coll
      (update-in [:EntryId :Id] str "_V1")
      (update-in [:TemporalExtent] (partial take 1))))

;; DIF 9

(defn dif-temporal
  "Returns the expected value of a parsed DIF 9 UMM record's :TemporalExtent."
  [temporal-extents]
  (->> temporal-extents
       ;; Periodic temporal extents are not supported in DIF 9, so we
       ;; must remove them.
       (remove :PeriodicDateTime)
       ;; Only ranges are supported by DIF 9, so we need to convert
       ;; single dates to range types.
       (map single-dates->ranges)
       (map #(dissoc % :TemporalRangeType :PrecisionOfSeconds :EndsAtPresentFlag))
       ;; Now we need to concatenate all of the range extents into a
       ;; single TemporalExtent.
       (reduce merge-ranges)
       ;; Turn that single one into a collection again.
       vector
       ;; Then make sure we get the right record type out.
       (map cmn/map->TemporalExtentType)))

(defmethod convert-internal :dif
  [umm-coll _]
  (update-in umm-coll [:TemporalExtent] dif-temporal))

;; ISO 19115-2

(defn expected-iso-19115-2-temporal
  [temporal-extents]
  (->> temporal-extents
       (map #(dissoc % :TemporalRangeType :PrecisionOfSeconds :EndsAtPresentFlag))
       (remove :PeriodicDateTime)
       (split-temporals :RangeDateTime)
       (split-temporals :SingleDateTime)
       (map cmn/map->TemporalExtentType)))

(defmethod convert-internal :iso19115
  [umm-coll _]
  (update-in umm-coll [:TemporalExtent] expected-iso-19115-2-temporal))

(defmethod convert-internal :iso-smap
  [umm-coll _]
  (convert-internal umm-coll :iso19115))

;;; Unimplemented Fields

(def not-implemented-fields
  "This is a list of required but not implemented fields."
  #{:Platform :ProcessingLevel :RelatedUrl :DataDate :ResponsibleOrganization :ScienceKeyword
    :SpatialExtent})

(defn- dissoc-not-implemented-fields
  "Removes not implemented fields since they can't be used for comparison"
  [record]
  (reduce (fn [r field]
            (assoc r field nil))
          record
          not-implemented-fields))

(def ^:private formats->expected-conversion-fns
  "A map of metadata formats to expected conversion functions"
  {:echo10 dissoc-not-implemented-fields
   :iso19115 dissoc-not-implemented-fields
   :iso-smap dissoc-not-implemented-fields
   :dif dissoc-not-implemented-fields
   :dif10 dissoc-not-implemented-fields})

(defn metadata-format->expected-conversion
  "Takes a metadata format and returns the function that can convert the UMM record used as input
  into the expected parsed UMM."
  [metadata-format]
  ;; identity is used if no conversion is needed.
  (get formats->expected-conversion-fns metadata-format identity))
;;; Public API

(defn convert
  "Returns input UMM-C record transformed according to the specified transformation for
  metadata-format."
  [umm-coll metadata-format]
  (dissoc-not-implemented-fields (convert-internal umm-coll metadata-format)))
