(ns cmr.umm-spec.test.iso-smap-expected-conversion
 "ISO SMAP specific expected conversion functionality"
 (:require [clj-time.core :as t]
           [clj-time.format :as f]
           [cmr.umm-spec.util :as su]
           [cmr.common.util :as util :refer [update-in-each]]
           [cmr.umm-spec.models.umm-common-models :as cmn]
           [cmr.umm-spec.test.expected-conversion-util :as conversion-util]
           [cmr.umm-spec.related-url :as ru-gen]
           [cmr.umm-spec.location-keywords :as lk]
           [cmr.umm-spec.test.location-keywords-helper :as lkt]
           [cmr.umm-spec.models.umm-collection-models :as umm-c]
           [cmr.umm-spec.date-util :as du]
           [cmr.umm-spec.iso-keywords :as kws]))


(defn- normalize-smap-instruments
  "Collects all instruments across given platforms and returns a seq of platforms with all
  instruments under each one."
  [platforms]
  (let [all-instruments (seq (mapcat :Instruments platforms))]
    (for [platform platforms]
      (assoc platform :Instruments all-instruments))))

(defn- expected-smap-iso-spatial-extent
  "Returns the expected SMAP ISO spatial extent"
  [spatial-extent]
  (when (get-in spatial-extent [:HorizontalSpatialDomain :Geometry :BoundingRectangles])
    (-> spatial-extent
        (assoc :SpatialCoverageType "HORIZONTAL" :GranuleSpatialRepresentation "GEODETIC")
        (assoc :VerticalSpatialDomains nil :OrbitParameters nil)
        (assoc-in [:HorizontalSpatialDomain :ZoneIdentifier] nil)
        (update-in [:HorizontalSpatialDomain :Geometry]
                   assoc :CoordinateSystem "GEODETIC" :Points nil :GPolygons nil :Lines nil)
        (update-in-each [:HorizontalSpatialDomain :Geometry :BoundingRectangles] assoc :CenterPoint nil)
        conversion-util/prune-empty-maps)))

(defn- expected-smap-data-dates
  "Returns the expected ISO SMAP DataDates."
  [data-dates]
  (if data-dates
    data-dates
    [(cmn/map->DateType {:Type "CREATE" :Date du/parsed-default-date})]))

(defn umm-expected-conversion-iso-smap
  [umm-coll original-brs]
  (-> umm-coll
        (update-in [:SpatialExtent] expected-smap-iso-spatial-extent)
        (update-in [:DataDates] expected-smap-data-dates)
        ;; ISO SMAP does not support the PrecisionOfSeconds field.
        (update-in-each [:TemporalExtents] assoc :PrecisionOfSeconds nil)
        ;; Implement this as part of CMR-2057
        (update-in-each [:TemporalExtents] assoc :TemporalRangeType nil)
        ;; Fields not supported by ISO-SMAP
        (assoc :MetadataAssociations nil) ;; Not supported for ISO SMAP
        (assoc :DataCenters [su/not-provided-data-center])
        (assoc :ContactGroups nil)
        (assoc :ContactPersons nil)
        (assoc :UseConstraints nil)
        (assoc :AccessConstraints nil)
        (assoc :SpatialKeywords nil)
        (assoc :TemporalKeywords nil)
        (assoc :CollectionDataType nil)
        (assoc :AdditionalAttributes nil)
        (assoc :ProcessingLevel (umm-c/map->ProcessingLevelType {:Id su/not-provided}))
        (assoc :Distributions nil)
        (assoc :Projects nil)
        (assoc :PublicationReferences nil)
        (assoc :AncillaryKeywords nil)
        (assoc :RelatedUrls [su/not-provided-related-url])
        (assoc :ISOTopicCategories nil)
        ;; Because SMAP cannot account for type, all of them are converted to Spacecraft.
        ;; Platform Characteristics are also not supported.
        (update-in-each [:Platforms] assoc :Type "Spacecraft" :Characteristics nil)
        ;; The following instrument fields are not supported by SMAP.
        (update-in-each [:Platforms] update-in-each [:Instruments] assoc
                        :Characteristics nil
                        :OperationalModes nil
                        :NumberOfSensors nil
                        :Sensors nil
                        :Technique nil)
        ;; ISO-SMAP checks on the Category of theme descriptive keywords to determine if it is
        ;; science keyword.
        (update-in [:ScienceKeywords]
                   (fn [sks]
                     (seq
                       (filter #(.contains kws/science-keyword-categories (:Category %)) sks))))
        (update-in [:Platforms] normalize-smap-instruments)
        (assoc :LocationKeywords nil)
        (assoc :PaleoTemporalCoverages nil)))
