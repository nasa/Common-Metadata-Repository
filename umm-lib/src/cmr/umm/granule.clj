(ns cmr.umm.granule
  "Defines the UMM Granule record."
  (:require [cmr.common.dev.record-pretty-printer :as record-pretty-printer]))

(defrecord CollectionRef
  [
   ;; maps to  Granule/Collection/DataSetId in echo granule schema
   entry-title

   ;; maps to Granule/Collection/ShortName
   short-name

   ;;  maps to Granule/Collection/VersionId
   version-id
   ])

(defrecord DataGranule
  [
   ;; maps to  Granule/DataGranule/ProducerGranuleID in echo granule schema
   producer-gran-id

   ;; maps to Granule/DataGranule/DayNight
   day-night

   ;; maps to Granule/DataGranule/ProductionDateTime
   production-date-time

   ;; maps to Granule/DataGranule/SizeMBDataGranule
   size
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

(defrecord SensorRef
  [
   short-name
  ])

(defrecord InstrumentRef
  [
   short-name
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
   ;; Only one of the following two should be present

   ;; A sequence of spatial points, bounding rectangles, polygons, and lines
   geometries
   ;; An alternative way to express spatial coverage - used for orbit backtracking
   orbit
   ])

(defrecord TwoDCoordinateSystem
  [
   name
   start-coordinate-1
   end-coordinate-1
   start-coordinate-2
   end-coordinate-2
   ])

(defrecord UmmGranule
  [
   ;; maps to Granule/GranuleUR in echo granule schema
   granule-ur

   data-provider-timestamps

   ;; granule parent
   collection-ref

   data-granule

   ;; A decimal number. Restriction flag in echo10
   access-value

   temporal

   spatial-coverage

   orbit-calculated-spatial-domains

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

(defn collection-ref
  ([entry-title]
   (map->CollectionRef {:entry-title entry-title}))
  ([short-name version-id]
   (map->CollectionRef {:short-name short-name :version-id version-id}))
  ([entry-title short-name version-id]
   (map->CollectionRef {:entry-title entry-title :short-name short-name :version-id version-id})))


(record-pretty-printer/enable-record-pretty-printing
  CollectionRef
  DataGranule
  GranuleTemporal
  OrbitCalculatedSpatialDomain
  Orbit
  ProductSpecificAttributeRef
  SensorRef
  InstrumentRef
  PlatformRef
  SpatialCoverage
  TwoDCoordinateSystem
  UmmGranule)