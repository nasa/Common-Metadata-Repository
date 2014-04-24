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

(def product-specific-attribute-types
  [:string :float :int :boolean :date :time :datetime :date-string :time-string :datetime-string])

(defrecord ProductSpecificAttribute
  [
   name
   description
   data-type
   parameter-range-begin
   parameter-range-end
   value
  ])

(defrecord Project
  [
   ;; maps to Echo10 Collection/Campaigns/Campaign/ShortName
   short-name

   ;;  maps to Echo10 Collection/Campaigns/Campaign/LongName
   long-name
   ])

(defrecord UmmCollection
  [
   ;; A combination of shortname and version id with an underscore.
   entry-id

   ;; The dataset-id in ECHO10
   entry-title

   product

   temporal-coverage

   product-specific-attributes

   ;; Records campaigns of ECHO10
   projects
   ])
