(ns cmr.umm.umm-collection
  "Defines the UMM Collection record. See the UMM Overview document for more information on the breakdown."
  (:require [cmr.common.dev.record-pretty-printer :as record-pretty-printer]))

(def not-provided
 "place holder string value for not provided string field"
 "Not provided")

(def not-provided-url
 "Placeholder valid url"
 "Not%20provided")

(defrecord Product
  [
   short-name
   long-name
   version-id
   version-description
   processing-level-id
   collection-data-type])


(defrecord DataProviderTimestamps
  [
   insert-time
   update-time
   delete-time
   revision-date-time
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
   group
   description
   data-type
   parameter-range-begin
   parameter-range-end
   value
   ;; We have both the original string format of parameter-range-begin, parameter-range-end and value
   ;; as well as parsed values based on the data-type of the additional attribute. When the values
   ;; are invalid for the data-type, the parsed values are nil. We save the original string format of
   ;; the values here so that we can provide the correct error message during UMM validation.
   parsed-parameter-range-begin
   parsed-parameter-range-end
   parsed-value
   ])

(defrecord Sensor
  [
   short-name
   long-name
   technique
   characteristics
   ])

(defrecord Instrument
  [
   short-name
   long-name
   technique
   sensors
   characteristics
   operation-modes
   ])

(defrecord Characteristic
  [
   name
   description
   data-type
   unit
   value
   ])

(defrecord Platform
  [
   short-name
   long-name
   type
   instruments
   characteristics
   ])

(defrecord CollectionAssociation
  [
   short-name
   version-id
   ])

(defrecord Project
  [
   ;; maps to Echo10 Collection/Campaigns/Campaign/ShortName
   short-name

   ;;  maps to Echo10 Collection/Campaigns/Campaign/LongName
   long-name
   ])

(defrecord Coordinate
  [
   min-value
   max-value
   ])

(defrecord TwoDCoordinateSystem
  [
   ;; maps to Echo10 Collection/TwoDCoordinateSystems/TwoDCoordinateSystem/TwoDCoordinateSystemName
   name
   coordinate-1
   coordinate-2
   ])

;; Note:archive-center is roughly equivalent to Custodian in ISO Role code list on which UMM relies
(def organization-types [:archive-center
                         :processing-center
                         :distribution-center
                         :originating-center])

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

   ;; Format is validated against values in KMS
   ;; https://cmr.sit.earthdata.nasa.gov/search/keywords/granule-data-format
   format

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

(defrecord Contact
  [
   ;; Phone, email, etc.
   type

   ;; Actual contact info (phone number, email address, etc.)
   value
   ])

(defrecord Personnel
  [
   first-name

   middle-name

   last-name

   ;; the roles of the person - investigator, technical contact, metadata author
   roles

   ;; contact points (email, phone, etc.)
   contacts])


(defrecord PublicationReference
  [
   author

   publication-date

   title

   series

   edition

   volume

   issue

   report-number

   publication-place

   publisher

   pages

   isbn

   ;; Digital Object Identifier
   doi

   related-url

   ;; String for miscellaneous content
   other-reference-details])

(def collection-progress-states
  [:planned :in-work :complete])

(defrecord UmmCollection
  [
   ;; The dataset-id in ECHO10
   entry-title

   summary

   ;; This element contains the summary of intentions with which this
   ;; collection was developed.
   purpose

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

   collection-associations

   ;; Records campaigns of ECHO10
   projects

   two-d-coordinate-systems

   related-urls

   ;; Records Archive Center, Processing Center
   organizations

   spatial-coverage

   associated-difs

   personnel

   ;; String containing the Language of the metadata
   metadata-language

   ;; String containing quality information
   quality

   ;; (String) The Use Constraints element is designed to protect
   ;; privacy and/or intellectual property by allowing the author to
   ;; specify how the item collection may or may not be used after
   ;; access is granted.
   use-constraints

   ;; (seq of PublicationReference) This element describes key bibliographic citations
   ;; pertaining to the data.
   publication-references

   ;; keyword; one of the values defined in collection-progress-states
   collection-progress

   ;; (seq of strings) This element describes DataSet Citations.
   collection-citations])



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
  Characteristic
  Project
  TwoDCoordinateSystem
  Organization
  RelatedURL
  OrbitParameters
  SpatialCoverage
  UmmCollection)
