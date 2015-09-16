(ns cmr.umm-spec.test.expected-conversion
  "This contains functions for manipulating the expected UMM record when taking a UMM record
  writing it to an XML format and parsing it back. Conversion from a UMM record into metadata
  can be lossy if some fields are not supported by that format"
  (:require [cmr.common.util :refer [update-in-each]]
            [cmr.umm-spec.models.collection :as umm-c]
            [cmr.umm-spec.json-schema :as js]
            [cmr.umm-spec.models.common :as cmn]
            [clj-time.core :as t]
            [cmr.common.util :as util]
            [cmr.umm-spec.umm-to-xml-mappings.dif10 :as dif10]))

(def example-record
  "An example record with fields supported by most formats."
  (js/coerce
   {:Platforms [{:ShortName "Platform 1"
                 :LongName "Example Platform Long Name 1"
                 :Type "Aircraft"
                 :Characteristics [{:Name "OrbitalPeriod"
                                    :Description "Orbital period in decimal minutes."
                                    :DataType "float"
                                    :Unit "Minutes"
                                    :Value "96.7"}]
                 :Instruments [{:ShortName "An Instrument"
                                :LongName "The Full Name of An Instrument v123.4"
                                :Technique "Two cans and a string"
                                :NumberOfSensors 1
                                :OperationalModes ["on" "off"]
                                :Characteristics [{:Name "Signal to Noise Ratio"
                                                   :Description "Is that necessary?"
                                                   :DataType "float"
                                                   :Unit "dB"
                                                   :Value "10"}]
                                :Sensors [{:ShortName "ABC"
                                           :LongName "Long Range Sensor"
                                           :Characteristics [{:Name "Signal to Noise Ratio"
                                                              :Description "Is that necessary?"
                                                              :DataType "float"
                                                              :Unit "dB"
                                                              :Value "10"}]
                                           :Technique "Drunken Fist"}]}]}]
    :TemporalExtents [{:TemporalRangeType "temp range"
                       :PrecisionOfSeconds 3
                       :EndsAtPresentFlag false
                       :RangeDateTimes [{:BeginningDateTime (t/date-time 2000)
                                         :EndingDateTime (t/date-time 2001)}
                                        {:BeginningDateTime (t/date-time 2002)
                                         :EndingDateTime (t/date-time 2003)}]}]
    :ProcessingLevel {:Id "3"
                      :ProcessingLevelDescription "Processing level description"}
    :RelatedUrls [{:URLs ["http://google.com"]}]
    :Organizations [{:Role "CUSTODIAN"
                     :Party {:OrganizationName {:ShortName "custodian"}}}]
    :ScienceKeywords [{:Category "cat" :Topic "top" :Term "ter"}]
    :SpatialExtent {:GranuleSpatialRepresentation "GEODETIC"
                    :HorizontalSpatialDomain {:ZoneIdentifier "Danger Zone"
                                              :Geometry {:CoordinateSystem "GEODETIC"
                                                         :BoundingRectangles [{:NorthBoundingCoordinate 45.0 :SouthBoundingCoordinate -81.0 :WestBoundingCoordinate 25.0 :EastBoundingCoordinate 30.0}]}}}
    :AccessConstraints {:Description "Access constraints"
                        :Value "0"}
    :UseConstraints "Use constraints"
    :EntryId "short_V1"
    :EntryTitle "The entry title V5"
    :Version "V5"
    :DataDates [{:Date (t/date-time 2012)
                 :Type "CREATE"}]
    :Abstract "A very abstract collection"
    :DataLanguage "English"
    :Projects [{:ShortName "project short_name"}]
    :Quality "Pretty good quality"}))

(defn- prune-empty-maps
  [x]
  (cond
    (map? x) (let [pruned (reduce (fn [m [k v]]
                                    (assoc m k (prune-empty-maps v)))
                                  x
                                  x)]
               (if (seq (keep val pruned))
                 pruned
                 nil))
    (vector? x) (when-let [pruned (prune-empty-maps (seq x))]
                  (vec pruned))
    (seq? x)    (seq (keep prune-empty-maps x))
    :else x))

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

;;; Format-Specific Translation Functions

(defn- convert-empty-record-to-nil
  [record]
  (if (seq (util/remove-nil-keys record))
    record
    nil))

(defn- expected-distributions
  "Returns the expected distributions for comparing with the distributions in the UMM-C record"
  [distributions]
  (->> distributions
       (keep convert-empty-record-to-nil)
       seq))

(defn- echo10-expected-distributions
  "Returns the ECHO10 expected distributions for comparing with the distributions in the UMM-C
  record. ECHO10 only has one Distribution, so here we just pick the first one."
  [distributions]
  (some-> distributions
          first
          convert-empty-record-to-nil
          (assoc :DistributionSize nil :DistributionMedia nil)
          vector))

;; ECHO 10

(defmethod convert-internal :echo10
  [umm-coll _]
  (-> umm-coll
      (update-in [:TemporalExtents] (comp seq (partial take 1)))
      (assoc :DataLanguage nil)
      (assoc :Quality nil)
      (assoc :UseConstraints nil)
      (update-in [:ProcessingLevel] convert-empty-record-to-nil)
      (update-in [:Distributions] echo10-expected-distributions)
      (update-in [:SpatialExtent] prune-empty-maps)
      (update-in-each [:AdditionalAttributes] assoc :Group nil :MeasurementResolution nil
                      :ParameterUnitsOfMeasure nil :ParameterValueAccuracy nil
                      :ValueAccuracyExplanation nil :UpdateDate nil)
      (update-in-each [:Projects] assoc :Campaigns nil)))

;; DIF 9

(defn dif9-temporal
  "Returns the expected value of a parsed DIF 9 UMM record's :TemporalExtents. All dates under
  SingleDateTimes are converted into ranges and concatenated with all ranges into a single
  TemporalExtentType."
  [temporal-extents]
  (let [singles (mapcat :SingleDateTimes temporal-extents)
        ranges (mapcat :RangeDateTimes temporal-extents)
        all-ranges (concat ranges
                           (map single-date->range singles))]
    (when (seq all-ranges)
      [(cmn/map->TemporalExtentType
         {:RangeDateTimes all-ranges})])))

(defn dif-access-constraints
  "Returns the expected value of a parsed DIF 9 and DIF 10 record's :AccessConstraints"
  [access-constraints]
  (when access-constraints
    (assoc access-constraints :Value nil)))

(defmethod convert-internal :dif
  [umm-coll _]
  (-> umm-coll
      (update-in [:TemporalExtents] dif9-temporal)
      (update-in [:SpatialExtent] assoc
                 :SpatialCoverageType nil
                 :OrbitParameters nil
                 :GranuleSpatialRepresentation nil
                 :VerticalSpatialDomains nil)
      (update-in [:SpatialExtent :HorizontalSpatialDomain] assoc
                 :ZoneIdentifier nil)
      (update-in [:SpatialExtent :HorizontalSpatialDomain :Geometry] assoc
                 :CoordinateSystem nil
                 :Points nil
                 :Lines nil
                 :GPolygons nil)
      (update-in-each [:SpatialExtent :HorizontalSpatialDomain :Geometry :BoundingRectangles] assoc
                      :CenterPoint nil)
      (update-in [:SpatialExtent] prune-empty-maps)
      (update-in [:AccessConstraints] dif-access-constraints)
      (update-in [:Distributions] expected-distributions)
      ;; DIF 9 does not support Platform Type or Characteristics. The mapping for Instruments is
      ;; unable to be implemented as specified.
      (update-in-each [:Platforms] assoc :Type nil :Characteristics nil :Instruments nil)
      (update-in [:ProcessingLevel] convert-empty-record-to-nil)
      (update-in-each [:AdditionalAttributes] assoc :Group "AdditionalAttribute")
      (update-in-each [:Projects] assoc :Campaigns nil :StartDate nil :EndDate nil)))


;; DIF 10
(defn dif10-platform
  [platform]
  ;; Only a limited subset of platform types are supported by DIF 10.
  (assoc platform :Type (get dif10/platform-types (:Type platform))))

(defn- dif10-processing-level
  [processing-level]
  (-> processing-level
      (assoc :ProcessingLevelDescription nil)
      (assoc :Id (get dif10/product-levels (:Id processing-level)))
      convert-empty-record-to-nil))

(defn dif10-project
  [proj]
  ;; DIF 10 only has at most one campaign in Project Campaigns
  (update-in proj [:Campaigns] #(when (first %) [(first %)])))

(defmethod convert-internal :dif10
  [umm-coll _]
  (-> umm-coll
      (assoc :SpatialExtent nil)
      (update-in [:AccessConstraints] dif-access-constraints)
      (update-in [:Distributions] expected-distributions)
      (update-in-each [:Platforms] dif10-platform)
      (update-in-each [:AdditionalAttributes] assoc :Group nil :UpdateDate nil)
      (update-in [:ProcessingLevel] dif10-processing-level)
      (update-in-each [:Projects] dif10-project)))

;; ISO 19115-2

(defn normalize-iso-19115-precisions
  "Returns seq of temporal extents all having the same precision as the first."
  [extents]
  (let [precision (-> extents first :PrecisionOfSeconds)]
    (map #(assoc % :PrecisionOfSeconds precision)
         extents)))

(defn sort-by-date-type-iso
  "Returns temporal extent records to match the order in which they are generated in ISO XML."
  [extents]
  (let [ranges (filter :RangeDateTimes extents)
        singles (filter :SingleDateTimes extents)]
    (seq (concat ranges singles))))

(defn expected-iso-19115-2-temporal
  [temporal-extents]
  (->> temporal-extents
       ;; ISO 19115-2 does not support these fields.
       (map #(assoc %
                    :TemporalRangeType nil
                    :EndsAtPresentFlag nil))
       normalize-iso-19115-precisions
       (split-temporals :RangeDateTimes)
       (split-temporals :SingleDateTimes)
       sort-by-date-type-iso))

(defmethod convert-internal :iso19115
  [umm-coll _]
  (-> umm-coll
      (assoc :SpatialExtent nil)
      (update-in [:TemporalExtents] expected-iso-19115-2-temporal)
      ;; The following platform instrument properties are not supported in ISO 19115-2
      (update-in-each [:Platforms] update-in-each [:Instruments] assoc
                      :NumberOfSensors nil
                      :OperationalModes nil)
      (assoc :Quality nil)
      (assoc :CollectionDataType nil)
      (update-in [:ProcessingLevel] convert-empty-record-to-nil)
      (assoc :Distributions nil)
      (assoc :AdditionalAttributes nil)
      (update-in-each [:Projects] assoc :Campaigns nil :StartDate nil :EndDate nil)))

;; ISO-SMAP

(defn- normalize-smap-instruments
  "Collects all instruments across given platforms and returns a seq of platforms with all
  instruments under each one."
  [platforms]
  (let [all-instruments (seq (mapcat :Instruments platforms))]
    (for [platform platforms]
      (assoc platform :Instruments all-instruments))))

(defmethod convert-internal :iso-smap
  [umm-coll _]
  (-> (convert-internal umm-coll :iso19115)
      ;; ISO SMAP does not support the PrecisionOfSeconds field.
      (update-in-each [:TemporalExtents] assoc :PrecisionOfSeconds nil)
      ;; Fields not supported by ISO-SMAP
      (assoc :UseConstraints nil)
      (assoc :AccessConstraints nil)
      (assoc :SpatialKeywords nil)
      (assoc :TemporalKeywords nil)
      (assoc :CollectionDataType nil)
      (assoc :AdditionalAttributes nil)
      (assoc :ProcessingLevel nil)
      (assoc :Distributions nil)
      (assoc :Projects nil)
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
      (update-in [:Platforms] normalize-smap-instruments)))

;;; Unimplemented Fields

(def not-implemented-fields
  "This is a list of required but not implemented fields."
  #{:CollectionCitations :MetadataDates :ISOTopicCategories :TilingIdentificationSystem
    :MetadataLanguage :DirectoryNames :Personnel :PublicationReferences
    :RelatedUrls :DataDates :Organizations
    :MetadataLineages :ScienceKeywords :SpatialInformation
    :AncillaryKeywords :PaleoTemporalCoverage
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
