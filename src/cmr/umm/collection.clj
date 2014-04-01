(ns cmr.umm.collection
  "Defines the UMM Collection record. See the UMM Overview document for more information on the breakdown.")

(defrecord Product
  [
   short-name
   long-name
   version-id
   ])

(defrecord RangeDateTime
  [
   beginning-date-time
   ending-date-time
   ])

(defrecord PeriodicDateTime
  [
   name
   start-date
   end-date
   duration-unit
   duration-value
   period-cycle-duration-unit
   period-cycle-duration-value
   ])

(defrecord TemporalCoverage
  [
   time-type
   date-type
   temporal-range-type
   precision-of-seconds
   ends-at-present-flag
   range-date-times
   single-date-times
   periodic-date-times
   ])

(defrecord UmmCollection
  [
   ;; A combination of shortname and version id with an underscore.
   entry-id

   ;; The dataset-id in ECHO10
   entry-title

   ;; Refers to a Product
   product

   ;; TemporalCoverage
   temporal-coverage
   ])

