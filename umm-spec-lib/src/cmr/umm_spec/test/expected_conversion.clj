(ns cmr.umm-spec.test.expected-conversion
  "This contains functions for manipulating the expected UMM record when taking a UMM record
  writing it to an XML format and parsing it back. Conversion from a UMM record into metadata
  can be lossy if some fields are not supported by that format"
  (:require [cmr.common.util :refer [update-in-each]]
            [cmr.umm-spec.models.common :as cmn]
            [cmr.umm-spec.umm-to-xml-mappings.dif10 :as dif10]))

(defmulti ^:private convert-internal
  "Returns UMM collection that would be expected when converting the source UMM-C record into the
  destination XML format and parsing it back to a UMM-C record."
  (fn [umm-coll metadata-format]
    metadata-format))

(defmethod convert-internal :default
  [umm-coll _]
  umm-coll)

;;; Utililty Functions

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
              (concat result (map #(assoc extent k [%])
                                  values))
              (concat result [extent])))
          []
          temporal-extents))

(defn merge-temporals
  "Merges a sequence of temporal extents into a single temporal extent. Not that any values in the
  subsequent temporal extents after the first one other than the times are thrown away."
  [temporal-extents]
  (when (seq temporal-extents)
    (reduce (fn [merged-temporal temporal]
                (-> merged-temporal
                    (update-in [:RangeDateTimes] #(seq (concat % (:RangeDateTimes temporal))))
                    (update-in [:SingleDateTimes] #(seq (concat % (:SingleDateTimes temporal))))
                    (update-in [:PeriodicDateTimes] #(seq (concat % (:PeriodicDateTimes temporal))))))
              (first temporal-extents)
              (rest temporal-extents))))

;;; Format-Specific Translation Functions

;; ECHO 10

(defmethod convert-internal :echo10
  [umm-coll _]
  (-> umm-coll
      (update-in [:TemporalExtents] (comp seq (partial take 1)))
      (assoc :DataLanguage nil)
      (assoc :Quality nil)
      (assoc :UseConstraints nil)))

;; DIF 9

(defn dif-temporal
  "Returns the expected value of a parsed DIF 9 UMM record's :TemporalExtents."
  [temporal-extents]
  (when-let [temporal (->> temporal-extents
                           ;; Only ranges are supported by DIF 9, so we need to convert
                           ;; single dates to range types.
                           (map single-dates->ranges)
                           ;; Merge the list of temporals together since we'll read the set of range date times as
                           ;; a single temporal with many range date times.
                           merge-temporals)]
    ;; DIF 9 does not support these fields.
    [(assoc temporal
            :PeriodicDateTimes nil
            :TemporalRangeType nil
            :PrecisionOfSeconds nil
            :EndsAtPresentFlag nil)]))

(defmethod convert-internal :dif
  [umm-coll _]
  (-> umm-coll
      (update-in [:TemporalExtents] dif-temporal)
      ;; DIF 9 does not support Platform Type or Characteristics. The mapping for Instruments is
      ;; unable to be implemented as specified.
      (update-in-each [:Platforms] assoc :Type nil :Characteristics nil :Instruments nil)))

;; DIF 10

(defn fix-dif10-platform
  [platform]
  ;; Only a limited subset of platform types are supported by DIF 10.
  (assoc platform :Type (get dif10/platform-types (:Type platform))))

(defmethod convert-internal :dif10
  [umm-coll _]
  (update-in-each umm-coll [:Platforms] fix-dif10-platform))

;; ISO 19115-2

(defn expected-iso-19115-2-temporal
  [temporal-extents]
  (->> temporal-extents
       ;; ISO 19115-2 does not support these fields.
       (map #(assoc %
                    :TemporalRangeType nil
                    :EndsAtPresentFlag nil))
       ;; ISO 19115-2 does not support PeriodicDateTimes.
       (remove :PeriodicDateTimes)
       (split-temporals :RangeDateTimes)
       (split-temporals :SingleDateTimes)
       (map cmn/map->TemporalExtentType)
       ;; Return nil instead of an empty seq to match the parsed value in case none of the inputs
       ;; are valid for ISO 19115-2.
       seq))

(defmethod convert-internal :iso19115
  [umm-coll _]
  (-> umm-coll
      (update-in [:TemporalExtents] expected-iso-19115-2-temporal)
      (assoc :Quality nil)))

(defmethod convert-internal :iso-smap
  [umm-coll _]
  (-> (convert-internal umm-coll :iso19115)
      ;; ISO SMAP does not support the PrecisionOfSeconds field.
      (update-in-each [:TemporalExtents] assoc :PrecisionOfSeconds nil)
      ;; Because SMAP cannot account for type, all of them are converted to Spacecraft.
      ;; Platform Characteristics are also not supported.
      (update-in-each [:Platforms] assoc :Type "Spacecraft" :Characteristics nil)
      (update-in-each [:Platforms] update-in-each [:Instruments] assoc
                      :Characteristics nil
                      :OperationalModes nil
                      :NumberOfSensors nil
                      :Technique nil)
      (assoc :UseConstraints nil)))

;;; Unimplemented Fields

(def not-implemented-fields
  "This is a list of required but not implemented fields."
  #{:CollectionCitations :MetadataDates :ISOTopicCategories :TilingIdentificationSystem
    :MetadataLanguage :DirectoryNames :ResponsiblePersonnel :PublicationReferences
    :RelatedUrls :DataDates :ResponsibleOrganizations :AccessConstraints :SpatialKeywords
    :SpatialExtent :MetadataLineages :AdditionalAttributes :ScienceKeywords :Distributions
    :CollectionProgress :SpatialInformation :CollectionDataType :TemporalKeywords
    :AncillaryKeywords :ProcessingLevel :Projects :PaleoTemporalCoverage
    :MetadataAssociations})

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
  ([umm-coll metadata-format]
   (if (= metadata-format :umm-json)
     umm-coll
     (dissoc-not-implemented-fields
      (convert-internal umm-coll metadata-format))))
  ([umm-coll src dest]
   (-> umm-coll
       (convert src)
       (convert dest))))
