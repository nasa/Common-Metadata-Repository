(ns cmr.umm.collection
  "Defines the UMM Collection record. See the UMM Overview document for more information on the breakdown.")

(defrecord Product
  [
   short-name
   long-name
   version-id
   processing-level-id
   collection-data-type
   ])

(defrecord DataProviderTimestamps
  [
   insert-time
   update-time
   delete-time
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

(defrecord Temporal
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

(defrecord ScienceKeyword
  [
   category
   topic
   term
   variable-level-1
   variable-level-2
   variable-level-3
   detailed-variable
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

(defrecord Sensor
  [
   short-name
  ])

(defrecord Instrument
  [
   short-name
   sensors
  ])

(defrecord Platform
  [
   short-name
   long-name
   type
   instruments
  ])

(defrecord Project
  [
   ;; maps to Echo10 Collection/Campaigns/Campaign/ShortName
   short-name

   ;;  maps to Echo10 Collection/Campaigns/Campaign/LongName
   long-name
   ])


(defrecord TwoDCoordinateSystem
  [
   ;; maps to Echo10 Collection/TwoDCoordinateSystems/TwoDCoordinateSystem/TwoDCoordinateSystemName
   name
   ])

(def organization-types [:archive-center :processing-center :distribution-center])

;; See CMR-202 issue description
(defrecord Organization
  [
   ;; maps to Echo10 Collection/ArchiveCenter | Collection/ProcessingCenter element names
   type

   ;; maps to Echo10 Collection/ArchiveCenter | Collection/ProcessingCenter element values
   org-name
   ])

(defrecord RelatedURL
  [
   type
   sub-type
   url
   description
  ])

(def granule-spatial-representations
  [:cartesian :geodetic :orbit :no-spatial])

(defrecord SpatialCoverage
  [
   ;; indicates the type of spatial representation for granules in the collection. (:orbit, :geodetic, etc.)
   granule-spatial-representation
   ])

(defrecord UmmCollection
  [
   ;; A combination of shortname and version id with an underscore.
   entry-id

   ;; The dataset-id in ECHO10
   entry-title

   product

   data-provider-timestamps

   spatial-keywords

   temporal

   science-keywords

   platforms

   product-specific-attributes

   ;; Records campaigns of ECHO10
   projects

   two-d-coordinate-systems

   related-urls

   ;; Records Archive Center, Processing Center
   organizations

   spatial-coverage
   ])
