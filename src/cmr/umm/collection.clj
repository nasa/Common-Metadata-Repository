(ns cmr.umm.collection
  "Defines the UMM Collection record. See the UMM Overview document for more information on the breakdown."
  (:require [cmr.common.dev.record-pretty-printer :as record-pretty-printer]))

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
   long-name
  ])

(defrecord Instrument
  [
   short-name
   long-name
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
   mime-type

   ;; Title is supposed to be a short description that is used for caption according to UMM-C doc,
   ;; but we construct it as either description (which could be really long)
   ;; or description plus resource-type. This is how catalog-rest does it too.
   ;; We may want to change the way title is constructed to make it shorter.
   title

   ;; only used by browse urls, it is the file size of the browse file referenced by the URL
   size
  ])

(def granule-spatial-representations
  [:cartesian :geodetic :orbit :no-spatial])

(def spatial-representations
  "Enumeration of collection spatial representations"
  [:cartesian :geodetic])

(defrecord OrbitParameters
  [
   ;; The width of the orbital track in kilometers
   swath-width

   ;; The period of the orbit in minutes
   period

   ;; The inclination angle of the orbit in degrees
   inclination-angle

   ;; The number of orbits per granule (may be fractional)
   number-of-orbits

   ;; The starting circular latitude in degrees
   start-circular-latitude
   ])

(defrecord SpatialCoverage
  [
   ;; indicates the type of spatial representation for granules in the collection. (:orbit, :geodetic, etc.)
   granule-spatial-representation

   ;; The spatial representation of shapes in the collection. Not required.
   spatial-representation

   ;; A sequence of spatial points, bounding rectangles, polygons, and lines.
   ;; If this is set then spatial-representation must be set as well.
   geometries

   ;; Parameters for the satellite with which the collection is associated
   orbit-parameters
   ])

(defrecord UmmCollection
  [
   ;; A combination of shortname and version id with an underscore.
   entry-id

   ;; The dataset-id in ECHO10
   entry-title

   summary

   product

   ;; A decimal number. Restriction flag in echo10
   access-value

   data-provider-timestamps

   spatial-keywords

   temporal-keywords

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

   associated-difs
   ])


(record-pretty-printer/enable-record-pretty-printing
  Product
  DataProviderTimestamps
  RangeDateTime
  PeriodicDateTime
  Temporal
  ScienceKeyword
  ProductSpecificAttribute
  Sensor
  Instrument
  Platform
  Project
  TwoDCoordinateSystem
  Organization
  RelatedURL
  OrbitParameters
  SpatialCoverage
  UmmCollection)
