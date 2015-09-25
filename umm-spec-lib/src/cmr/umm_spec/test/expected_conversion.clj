(ns cmr.umm-spec.test.expected-conversion
  "This contains functions for manipulating the expected UMM record when taking a UMM record
  writing it to an XML format and parsing it back. Conversion from a UMM record into metadata
  can be lossy if some fields are not supported by that format"
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [clojure.string :as str]
            [cmr.common.util :as util :refer [update-in-each]]
            [cmr.umm-spec.util :as su]
            [cmr.umm-spec.json-schema :as js]
            [cmr.umm-spec.models.collection :as umm-c]
            [cmr.umm-spec.models.common :as cmn]
            [cmr.umm-spec.umm-to-xml-mappings.dif10 :as dif10]
            [cmr.umm-spec.umm-to-xml-mappings.echo10.spatial :as echo10-spatial-gen]
            [cmr.umm-spec.umm-to-xml-mappings.echo10.related-url :as echo10-ru-gen]
            [cmr.umm-spec.xml-to-umm-mappings.echo10.spatial :as echo10-spatial-parse]))

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
     :Organizations [{:Role "CUSTODIAN"
                      :Party {:OrganizationName {:ShortName "custodian"}}}]
     :ScienceKeywords [{:Category "cat" :Topic "top" :Term "ter"}]
     :SpatialExtent {:GranuleSpatialRepresentation "GEODETIC"
                     :HorizontalSpatialDomain {:ZoneIdentifier "Danger Zone"
                                               :Geometry {:CoordinateSystem "GEODETIC"
                                                          :BoundingRectangles [{:NorthBoundingCoordinate 45.0 :SouthBoundingCoordinate -81.0 :WestBoundingCoordinate 25.0 :EastBoundingCoordinate 30.0}]}}
                     :VerticalSpatialDomains [{:Type "Some kind of type"
                                               :Value "Some kind of value"}]}
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
     :Quality "Pretty good quality"
     :PublicationReferences [{:PublicationDate (t/date-time 2015)
                              :OtherReferenceDetails "Other reference details"
                              :Series "series"
                              :Title "title"
                              :DOI {:DOI "doi:xyz"
                                    :Authority "DOI"}
                              :Pages "100"
                              :Edition "edition"
                              :ReportNumber "25"
                              :Volume "volume"
                              :Publisher "publisher"
                              :RelatedUrl {:URLs ["www.foo.com" "www.shoo.com"]}
                              :ISBN "ISBN"
                              :Author "author"
                              :Issue "issue"
                              :PublicationPlace "publication place"}
                             {:DOI {:DOI "identifier"
                                    :Authority "authority"}}
                             {:Title "some title"}]
     :TemporalKeywords ["temporal keyword 1" "temporal keyword 2"]
     :AncillaryKeywords ["ancillary keyword 1" "ancillary keyword 2"]
     :RelatedUrls [{:Description "Related url description"
                    :ContentType {:Type "GET DATA" :Subtype "sub type"}
                    :Protocol "protocol"
                    :URLs ["www.foo.com", "www.shoo.com"]
                    :Title "related url title"
                    :MimeType "mime type"
                    :Caption "caption"}
                   {:Description "Related url 3 description "
                    :ContentType {:Type "Some type" :Subtype "sub type"}
                    :URLs ["www.foo.com"]}
                   {:Description "Related url 2 description"
                    :ContentType {:Type "GET RELATED VISUALIZATION" :Subtype "sub type"}
                    :URLs ["www.foo.com"]
                    :FileSize {:Size 10.0 :Unit "MB"}}]}))

(defn- prune-empty-maps
  "If x is a map, returns nil if all of the map's values are nil, otherwise returns the map with
  prune-empty-maps applied to all values. If x is a collection, returns the result of keeping the
  non-nil results of calling prune-empty-maps on each value in x."
  [x]
  (cond
    (map? x) (let [pruned (reduce (fn [m [k v]]
                                    (assoc m k (prune-empty-maps v)))
                                  x
                                  x)]
               (when (seq (keep val pruned))
                 pruned))
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

(defn- date-time->date
  "Returns the given datetime to a date."
  [date-time]
  (some->> date-time
           (f/unparse (f/formatters :date))
           (f/parse (f/formatters :date))))

;;; Format-Specific Translation Functions

(defn- echo10-expected-distributions
  "Returns the ECHO10 expected distributions for comparing with the distributions in the UMM-C
  record. ECHO10 only has one Distribution, so here we just pick the first one."
  [distributions]
  (some-> distributions
          first
          su/convert-empty-record-to-nil
          (assoc :DistributionSize nil :DistributionMedia nil)
          vector))

;; ECHO 10

(defn fix-echo10-polygon
  "Because the generated points may not be in valid UMM order (closed and CCW), we need to do some
  fudging here."
  [gpolygon]
  (let [fix-points (fn [points]
                     (-> points
                         echo10-spatial-gen/echo-point-order
                         echo10-spatial-parse/umm-point-order))]
    (-> gpolygon
        (update-in [:Boundary :Points] fix-points)
        (update-in-each [:ExclusiveZone :Boundaries] update-in [:Points] fix-points))))

(defn- expected-echo10-related-urls
  [related-urls]
  (seq (for [related-url related-urls
             :let [type (get-in related-url [:ContentType :Type])]
             url (:URLs related-url)]
         (-> related-url
             (assoc :Protocol nil :Title nil :Caption nil :URLs [url])
             (update-in [:FileSize] (fn [file-size]
                                      (when (and file-size
                                                 (= type "GET RELATED VISUALIZATION"))
                                        (when-let [byte-size (echo10-ru-gen/convert-to-bytes
                                                               (:Size file-size) (:Unit file-size))]
                                          (assoc file-size :Size (float (int byte-size)) :Unit "Bytes")))))
             (assoc-in [:ContentType :Subtype] nil)
             (update-in [:ContentType]
                        (fn [content-type]
                          (when (#{"GET DATA"
                                   "GET RELATED VISUALIZATION"
                                   "VIEW RELATED INFORMATION"} type)
                            content-type)))))))

(defmethod convert-internal :echo10
  [umm-coll _]
  (-> umm-coll
      (update-in [:TemporalExtents] (comp seq (partial take 1)))
      (assoc :DataLanguage nil)
      (assoc :Quality nil)
      (assoc :UseConstraints nil)
      (assoc :PublicationReferences nil)
      (assoc :AncillaryKeywords nil)
      (update-in [:ProcessingLevel] su/convert-empty-record-to-nil)
      (update-in [:Distributions] echo10-expected-distributions)
      (update-in-each [:SpatialExtent :HorizontalSpatialDomain :Geometry :GPolygons] fix-echo10-polygon)
      (update-in [:SpatialExtent] prune-empty-maps)
      (update-in-each [:AdditionalAttributes] assoc :Group nil :MeasurementResolution nil
                      :ParameterUnitsOfMeasure nil :ParameterValueAccuracy nil
                      :ValueAccuracyExplanation nil :UpdateDate nil)
      (update-in-each [:Projects] assoc :Campaigns nil)
      (update-in [:RelatedUrls] expected-echo10-related-urls)
      ;; ECHO10 requires Price to be %9.2f which maps to UMM JSON DistributionType Fees
      (update-in-each [:Distributions] update-in [:Fees]
                      (fn [n]
                        (when n (Double. (format "%9.2f" n)))))))

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

(defn dif-publication-reference
  "Returns the expected value of a parsed DIF 9 publication reference"
  [pub-ref]
  (-> pub-ref
      (update-in [:DOI] (fn [doi] (when doi (assoc doi :Authority nil))))
      (update-in [:RelatedUrl]
                 (fn [related-url]
                   (when related-url (assoc related-url
                                            :URLs (seq (remove nil? [(first (:URLs related-url))]))
                                            :Description nil
                                            :ContentType nil
                                            :Protocol nil
                                            :Title nil
                                            :MimeType nil
                                            :Caption nil
                                            :FileSize nil))))))

(defn- expected-dif-related-urls
  [related-urls]
  (seq (for [related-url related-urls]
         (assoc related-url :Protocol nil :Title nil :Caption nil :FileSize nil :MimeType nil))))

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
      (update-in [:Distributions] su/remove-empty-records)
      ;; DIF 9 does not support Platform Type or Characteristics. The mapping for Instruments is
      ;; unable to be implemented as specified.
      (update-in-each [:Platforms] assoc :Type nil :Characteristics nil :Instruments nil)
      (update-in [:ProcessingLevel] su/convert-empty-record-to-nil)
      (update-in-each [:AdditionalAttributes] assoc :Group "AdditionalAttribute")
      (update-in-each [:Projects] assoc :Campaigns nil :StartDate nil :EndDate nil)
      (update-in-each [:PublicationReferences] dif-publication-reference)
      (update-in [:RelatedUrls] expected-dif-related-urls)))


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
      su/convert-empty-record-to-nil))

(defn dif10-project
  [proj]
  (-> proj
      ;; DIF 10 only has at most one campaign in Project Campaigns
      (update-in [:Campaigns] #(when (first %) [(first %)]))
      ;; DIF10 StartDate and EndDate are date rather than datetime
      (update-in [:StartDate] date-time->date)
      (update-in [:EndDate] date-time->date)))

(defn trim-dif10-geometry
  "Returns GeometryType record with a maximium of one value in the collections under each key."
  [geom]
  ;; The shape key sequence here must be in the same order as in the DIF 10 XML generation.
  (let [shape-keys [:GPolygons :BoundingRectangles :Lines :Points]
        found-shape (first (filter #(seq (get geom %)) shape-keys))
        other-keys (remove #{found-shape} shape-keys)
        geom (if found-shape
               (update-in geom [found-shape] #(take 1 %))
               geom)]
    (reduce (fn [m k]
              (assoc m k nil))
            geom
            other-keys)))

(defmethod convert-internal :dif10
  [umm-coll _]
  (-> umm-coll
      (update-in [:SpatialExtent :HorizontalSpatialDomain :Geometry] trim-dif10-geometry)
      (update-in [:SpatialExtent] prune-empty-maps)
      (update-in [:AccessConstraints] dif-access-constraints)
      (update-in [:Distributions] su/remove-empty-records)
      (update-in-each [:Platforms] dif10-platform)
      (update-in-each [:AdditionalAttributes] assoc :Group nil :UpdateDate nil)
      (update-in [:ProcessingLevel] dif10-processing-level)
      (update-in-each [:Projects] dif10-project)
      (update-in [:PublicationReferences] prune-empty-maps)
      (update-in-each [:PublicationReferences] dif-publication-reference)
      (update-in [:RelatedUrls] expected-dif-related-urls)))

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

(defn iso-19115-2-publication-reference
  "Returns the expected value of a parsed ISO-19115-2 publication references"
  [pub-refs]
  (seq (for [pub-ref pub-refs
             :when (and (:Title pub-ref) (:PublicationDate pub-ref))]
         (-> pub-ref
             (assoc :ReportNumber nil :Volume nil :RelatedUrl nil :PublicationPlace nil)
             (update-in [:DOI] (fn [doi] (when doi (assoc doi :Authority nil))))
             (update-in [:PublicationDate] date-time->date)))))

(defn- expected-iso-19115-2-distributions
  "Returns the expected ISO19115-2 distributions for comparison."
  [distributions]
  (some->> distributions
           su/remove-empty-records
           vec))

(defn- expected-iso-19115-2-related-urls
  [related-urls]
  (seq (for [related-url related-urls
             url (:URLs related-url)]
         (-> related-url
             (assoc :Protocol nil :Title nil :MimeType nil :Caption nil :FileSize nil :URLs [url])
             (assoc-in [:ContentType :Subtype] nil)
             (update-in [:ContentType]
                        (fn [content-type]
                          (when (#{"GET DATA"
                                   "GET RELATED VISUALIZATION"
                                   "VIEW RELATED INFORMATION"} (:Type content-type))
                            content-type)))))))
(defn- fix-iso-vertical-spatial-domain-values
  [vsd]
  (let [fix-val (fn [x]
                  (when x
                    (let [x-replaced (str/replace x #"[,=]" "")]
                      (when-not (str/blank? x-replaced)
                        x-replaced))))]
    (-> vsd
        (update-in [:Type] fix-val)
        (update-in [:Value] fix-val))))

(defn update-iso-spatial
  [spatial-extent]
  (-> spatial-extent
      (assoc :OrbitParameters nil)
      (assoc-in [:HorizontalSpatialDomain :ZoneIdentifier] nil)
      (update-in-each [:HorizontalSpatialDomain :Geometry :BoundingRectangles] assoc :CenterPoint nil)
      (update-in-each [:HorizontalSpatialDomain :Geometry :Lines] assoc :CenterPoint nil)
      (update-in-each [:HorizontalSpatialDomain :Geometry :GPolygons] assoc :CenterPoint nil)
      (update-in [:VerticalSpatialDomains] #(take 1 %))
      (update-in-each [:VerticalSpatialDomains] fix-iso-vertical-spatial-domain-values)
      prune-empty-maps))

(defmethod convert-internal :iso19115
  [umm-coll _]
  (-> umm-coll
      (update-in [:SpatialExtent] update-iso-spatial)
      (update-in [:TemporalExtents] expected-iso-19115-2-temporal)
      ;; The following platform instrument properties are not supported in ISO 19115-2
      (update-in-each [:Platforms] update-in-each [:Instruments] assoc
                      :NumberOfSensors nil
                      :OperationalModes nil)
      (assoc :Quality nil)
      (assoc :CollectionDataType nil)
      (update-in [:DataLanguage] #(or % "eng"))
      (update-in [:ProcessingLevel] su/convert-empty-record-to-nil)
      (update-in [:Distributions] expected-iso-19115-2-distributions)
      (assoc :AdditionalAttributes nil)
      (update-in-each [:Projects] assoc :Campaigns nil :StartDate nil :EndDate nil)
      (update-in [:PublicationReferences] iso-19115-2-publication-reference)
      (update-in [:RelatedUrls] expected-iso-19115-2-related-urls)))

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
      (assoc :SpatialExtent nil)
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
      (assoc :PublicationReferences nil)
      (assoc :AncillaryKeywords nil)
      (assoc :RelatedUrls nil)
      (assoc :ScienceKeywords nil)
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
    :MetadataLanguage :DirectoryNames :Personnel
    :DataDates :Organizations
    :MetadataLineages :SpatialInformation :PaleoTemporalCoverage
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
     (-> umm-coll
         (convert-internal metadata-format)
         dissoc-not-implemented-fields)))
  ([umm-coll src dest]
   (-> umm-coll
       (convert src)
       (convert dest))))
