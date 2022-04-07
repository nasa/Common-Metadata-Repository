(ns cmr.umm.umm-granule
  "Defines the UMM Granule record."
  (:require
   [cmr.common.dev.record-pretty-printer :as record-pretty-printer]))

(defrecord CollectionRef
  [
   ;; maps to  Granule/Collection/DataSetId in echo granule schema
   entry-title

   ;; maps to Granule/Collection/ShortName
   short-name

   ;;  maps to Granule/Collection/VersionId
   version-id

   ;; maps to  Granule/Collection/EntryId in echo granule schema
   entry-id
   ])

(defrecord DataProviderTimestamps
  [
   insert-time
   update-time
   delete-time
   ])

(defrecord Checksum
  [
   value
   algorithm
   ])

(defrecord DataGranule
  [
   ;; maps to  Granule/DataGranule/ProducerGranuleID in echo granule schema
   producer-gran-id

   ;; maps to Granule/DataGranule/DayNight
   day-night

   ;; maps to Granule/DataGranule/ProductionDateTime
   production-date-time

   ;; maps to Granule/DataGranule/DataGranuleSizeInBytes
   size-in-bytes

   ;; maps to Granule/DataGranule/SizeMBDataGranule in echo10 granule schema
   size

   ;; maps to SizeUnit in UMM-G schema
   size-unit

   ;; Format is validated against values in KMS
   ;; https://cmr.sit.earthdata.nasa.gov/search/keywords/granule-data-format
   format

   files

   ;; maps to Granule/DataGranule/Checksum
   checksum

   ;; currently not mapped to any echo10/iso-smap granule schema.
   crid-ids

   ;; currently not mapped to any echo10/iso-smap granule schema.
   feature-ids
   ])

(defrecord PGEVersionClass
  [
   pge-name

   pge-version
  ])

(defrecord GranuleTemporal
  [
   range-date-time
   single-date-time
   ])

(defrecord OrbitCalculatedSpatialDomain
  [
   orbital-model-name
   orbit-number
   start-orbit-number
   stop-orbit-number
   equator-crossing-longitude
   equator-crossing-date-time
   ])

(defrecord Orbit
  [
   ascending-crossing ; lon of equator crossing
   start-lat
   start-direction ; :asc or :desc (ascending or descending)
   end-lat
   end-direction ; :asc or :desc (ascending or descending)
   ])

;; A reference to a product specific attribute in the parent collection. The attribute reference may
;; contain a granule specific value that will override the value in the parent collection for this
;; granule. An attribute with the same name must exist in the parent collection.
(defrecord ProductSpecificAttributeRef
  [
   name
   values
  ])

(defrecord CharacteristicRef
  [
   name
   value
  ])

(defrecord SensorRef
  [
   short-name
   characteristic-refs
  ])

(defrecord InstrumentRef
  [
   short-name
   characteristic-refs
   sensor-refs
   operation-modes
  ])

(defrecord PlatformRef
  [
   short-name
   instrument-refs
  ])

(defrecord SpatialCoverage
  [
   ;; Only one of geometries and orbit should be present

   ;; A sequence of spatial points, bounding rectangles, polygons, and lines
   geometries
   ;; An alternative way to express spatial coverage - used for orbit backtracking
   orbit
   ;; This element stores track information of the granule. Track information is used to allow a
   ;; user to search for granules whose spatial extent is based on an orbital cycle, pass, and tile
   ;; mapping. Though it is derived from the SWOT mission requirements, it is intended that this
   ;; element type be generic enough so that other missions can make use of it. While track
   ;; information is a type of spatial domain, it is expected that the metadata provider will
   ;; provide geometry information that matches the spatial extent of the track information.
   track
   ])

(defrecord TwoDCoordinateSystem
  [
   name
   start-coordinate-1
   end-coordinate-1
   start-coordinate-2
   end-coordinate-2
   ])

(defrecord QAStats
  [
   qa-percent-missing-data
   qa-percent-out-of-bounds-data
   qa-percent-interpolated-data
   qa-percent-cloud-cover
   ])

(defrecord QAFlags
  [
   automatic-quality-flag
   automatic-quality-flag-explanation
   operational-quality-flag
   operational-quality-flag-explanation
   science-quality-flag
   science-quality-flag-explanation
   ])

(defrecord MeasuredParameter
  [
   parameter-name
   qa-stats
   qa-flags
   ])

(defrecord Track
  [
   cycle
   passes
   ])

(defrecord TrackPass
  [
   pass
   tiles
   ])

(defrecord File
  [
   name
   size-in-bytes
   size size-unit
   format
   mime-type
   checksum
   ])

(defrecord UmmGranule
  [
   ;; maps to Granule/GranuleUR in echo granule schema
   granule-ur

   data-provider-timestamps

   ;; granule parent
   collection-ref

   data-granule

   pge-version-class

   ;; A decimal number. Restriction flag in echo10
   access-value

   temporal

   spatial-coverage

   orbit-calculated-spatial-domains

   measured-parameters

   platform-refs

   ;; A sequence of short names of projects (aka campaigns) reference parent short names
   project-refs

   ;; references to onlineResources and onlineAccessURLs
   related-urls

   ;; reference to PSAs in the parent collection
   product-specific-attributes

   ;; maps to Granule/CloudCover in echo granule schema
   cloud-cover

   two-d-coordinate-system
   ])

(record-pretty-printer/enable-record-pretty-printing
  CollectionRef
  DataGranule
  PGEVersionClass
  GranuleTemporal
  OrbitCalculatedSpatialDomain
  Orbit
  ProductSpecificAttributeRef
  SensorRef
  InstrumentRef
  PlatformRef
  SpatialCoverage
  TwoDCoordinateSystem
  QAStats
  QAFlags
  MeasuredParameter
  Track
  TrackPass
  UmmGranule)
