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
   technique
   ])

(defrecord Instrument
  [
   short-name
   long-name
   technique
   sensors
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

;; See CMR-202 issue description
(defrecord Organization
  [
   ;; maps to Echo10 Collection/ArchiveCenter | Collection/ProcessingCenter element names
   org-type

   ;; maps to Echo10 Collection/ArchiveCenter | Collection/ProcessingCenter element values
   org-name

   ;; Contact information for the data including name, email, phone, FAX, and address information.
   personnel
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

(defrecord Phone
  [
   ;; Number of the organization or individual who is point of contact.
   number

   ;; The type of telephone number being provided in this instance of the phone number.
   number-type
   ])

(defrecord Address
  [
   ;; The city of the person or organization.
   city

   ;; The country of the address.
   country

   ;; The zip or other postal code of the address.
   postal-code

   ;; The state or province of the address.
   state-province

   ;; A list of address lines for the address, used for mailing or physical addresses of organizations
   ;; or individuals who serve as points of contact.
   street-address-lines
   ])

(defrecord ContactPerson
  [
   ;; List where each entry is ne of the following: Investigator, Technical Contact, or
   ;; Metadata Author.
   roles

   ;; This entity contains the address details for each contact.
   addresses

   ;; The list of addresses of the electronic mailbox of the organization or individual.
   emails

   ;; First name of the individual which the contact applies.
   first-name

   ;; Last name of the individual which the contact applies.
   last-name

   ;; Middle name of the individual which the contact applies.
   middle-name

   ;; The list of telephone details associated with the contact.
   phones
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
  Characteristic
  Project
  TwoDCoordinateSystem
  Organization
  RelatedURL
  OrbitParameters
  SpatialCoverage
  UmmCollection
  ContactPerson
  Phone)
