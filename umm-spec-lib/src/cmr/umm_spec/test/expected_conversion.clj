(ns cmr.umm-spec.test.expected-conversion
  "This contains functions for manipulating the expected UMM record when taking a UMM record
  writing it to an XML format and parsing it back. Conversion from a UMM record into metadata
  can be lossy if some fields are not supported by that format"
  (:require [cmr.umm-spec.models.common :as cmn]))

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

(defn- expected-echo10
  "This manipulates the expected parsed UMM record based on lossy conversion in ECHO10."
  [umm-coll]
  ;; ECHO10 returns entry id as a combination of short name and version. It generates short name
  ;; from entry id. So the expected entry id when going from umm->echo10->umm is the original
  ;; entry id concatenated with the version id.
  (update-in umm-coll [:EntryId :Id] str "_V1"))

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

(defn expected-dif
  "Returns a UMM record with only features supported by DIF 9."
  [umm-coll]
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

(defn expected-iso-19115-2
  [iso-coll]
  (update-in iso-coll [:TemporalExtent] expected-iso-19115-2-temporal))

;;; Conversion Lookup By Format

(def ^:private formats->expected-conversion-fns
  "A map of metadata formats to expected conversion functions"
  {:echo10   expected-echo10
   :dif      expected-dif
   :iso19115 expected-iso-19115-2})

(defn- metadata-format->expected-conversion
  "Takes a metadata format and returns the function that can convert the UMM record used as input
  into the expected parsed UMM."
  [metadata-format]
  ;; identity is used if no conversion is needed.
  (get formats->expected-conversion-fns metadata-format identity))

;;; Public API

(defn convert
  "Returns input-record transformed according to the specified
  transformation for metadata-format."
  [input-record metadata-format]
  (let [f (metadata-format->expected-conversion metadata-format)]
    (f input-record)))
