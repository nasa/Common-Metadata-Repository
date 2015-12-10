(ns cmr.umm-spec.test.expected-conversion
  "This contains functions for manipulating the expected UMM record when taking a UMM record
  writing it to an XML format and parsing it back. Conversion from a UMM record into metadata
  can be lossy if some fields are not supported by that format"
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [clojure.string :as str]
            [cmr.common.util :as util :refer [update-in-each]]
            [cmr.umm-spec.util :as su]
            [cmr.umm-spec.iso19115-2-util :as iso]
            [cmr.umm-spec.iso-keywords :as kws]
            [cmr.umm-spec.json-schema :as js]
            [cmr.umm-spec.models.collection :as umm-c]
            [cmr.umm-spec.models.common :as cmn]
            [cmr.umm-spec.umm-to-xml-mappings.dif10 :as dif10]
            [cmr.umm-spec.umm-to-xml-mappings.echo10.spatial :as echo10-spatial-gen]
            [cmr.umm-spec.umm-to-xml-mappings.echo10.related-url :as echo10-ru-gen]
            [cmr.umm-spec.xml-to-umm-mappings.echo10.spatial :as echo10-spatial-parse]
            [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.additional-attribute :as iso-aa]))

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
     :ScienceKeywords [{:Category "EARTH SCIENCE" :Topic "top" :Term "ter"}
                       {:Category "EARTH SCIENCE SERVICES" :Topic "topic" :Term "term"
                        :VariableLevel1 "var 1" :VariableLevel2 "var 2"
                        :VariableLevel3 "var 3" :DetailedVariable "detailed"}]
     :SpatialKeywords ["SPK1" "SPK2"]
     :SpatialExtent {:GranuleSpatialRepresentation "GEODETIC"
                     :HorizontalSpatialDomain {:ZoneIdentifier "Danger Zone"
                                               :Geometry {:CoordinateSystem "GEODETIC"
                                                          :BoundingRectangles [{:NorthBoundingCoordinate 45.0 :SouthBoundingCoordinate -81.0 :WestBoundingCoordinate 25.0 :EastBoundingCoordinate 30.0}]}}
                     :VerticalSpatialDomains [{:Type "Some kind of type"
                                               :Value "Some kind of value"}]
                     :OrbitParameters {:SwathWidth 2.0
                                       :Period 96.7
                                       :InclinationAngle 94.0
                                       :NumberOfOrbits 2.0
                                       :StartCircularLatitude 50.0}}
     :TilingIdentificationSystem {:TilingIdentificationSystemName "Tiling System Name"
                                  :Coordinate1 {:MinimumValue 1.0
                                                :MaximumValue 10.0}
                                  :Coordinate2 {:MinimumValue 1.0
                                                :MaximumValue 10.0}}
     :AccessConstraints {:Description "Restriction Comment: Access constraints"
                         :Value "0"}
     :UseConstraints "Restriction Flag: Use constraints"
     :Distributions [{:DistributionSize 10.0
                      :DistributionMedia "8 track"
                      :DistributionFormat "Animated GIF"
                      :Fees "Gratuit-Free"}
                     {:DistributionSize 100000000000.0
                      :DistributionMedia "Download"
                      :DistributionFormat "Bits"
                      :Fees "0.99"}]
     :EntryTitle "The entry title V5"
     :ShortName "Short"
     :Version "V5"
     :DataDates [{:Date (t/date-time 2012)
                  :Type "CREATE"}]
     :Abstract "A very abstract collection"
     :DataLanguage "English"
     :CollectionDataType "SCIENCE_QUALITY"
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
                    :Protocol "ftp"
                    :ContentType {:Type "GET RELATED VISUALIZATION" :Subtype "sub type"}
                    :URLs ["www.foo.com"]
                    :FileSize {:Size 10.0 :Unit "MB"}}]
     :MetadataAssociations [{:Type "SCIENCE ASSOCIATED"
                             :Description "Associated with a collection"
                             :EntryId "AssocEntryId"
                             :Version "V8"},
                            {:Type "INPUT"
                             :Description "Some other collection"
                             :EntryId "AssocEntryId2"
                             :Version "V2"}
                            {:Type nil
                             :Description nil
                             :EntryId "AssocEntryId3"
                             :Version nil}
                            {:Type "INPUT"
                             :EntryId "AssocEntryId4"}]
     :AdditionalAttributes [{:Group "Accuracy"
                             :Name "PercentGroundHit"
                             :DataType "FLOAT"
                             :Description "Percent of data for this granule that had a detected ground return of the transmitted laser pulse."
                             :MeasurementResolution "1"
                             :ParameterRangeBegin "0.0"
                             :ParameterRangeEnd "100.0"
                             :ParameterUnitsOfMeasure "Percent"
                             :Value "50"
                             :ParameterValueAccuracy "1"
                             :ValueAccuracyExplanation "explaination for value accuracy"}
                            {:Name "aa-name"
                             :DataType "INT"}]
     :Organizations [{:Role "ORIGINATOR"
                      :Party {:ServiceHours "24/7"
                              :OrganizationName {:ShortName "org 1"
                                                 :LongName "longname"}
                              :Addresses [{:StreetAddresses ["23 abc st"]
                                           :City "city"}]}}
                     {:Role "POINTOFCONTACT"
                      :Party {:Person {:LastName "person 1"}
                              :RelatedUrls [{:Description "Organization related url description"
                                             :ContentType {:Type "Some type" :Subtype "sub type"}
                                             :URLs ["www.foo.com"]}]}}
                     {:Role "DISTRIBUTOR"
                      :Party {:OrganizationName {:ShortName "org 2"}
                              :Contacts [{:Type "email" :Value "abc@foo.com"}]}}
                     {:Role "PROCESSOR"
                      :Party {:OrganizationName {:ShortName "org 3"}}}]
     :Personnel [{:Role "POINTOFCONTACT"
                  :Party {:Person {:LastName "person 2"}}}]
     }))

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

(defn fixup-dif10-data-dates
  "Returns DataDates seq as it would be parsed from ECHO and DIF 10 XML document."
  [data-dates]
  (when (seq data-dates)
    (let [date-types (group-by :Type data-dates)]
      (filter some?
              (for [date-type ["CREATE" "UPDATE" "REVIEW" "DELETE"]]
                (last (sort-by :Date (get date-types date-type))))))))

(defn fixup-echo10-data-dates
  [data-dates]
  (seq
    (remove #(= "REVIEW" (:Type %))
            (fixup-dif10-data-dates data-dates))))

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

(defn- echo10-expected-fees
  "Returns the fees if it is a number string, i.e., can be converted to a decimal, otherwise nil."
  [fees]
  (when fees
    (try
      (format "%9.2f" (Double. fees))
      (catch NumberFormatException e))))

(defn- echo10-expected-distributions
  "Returns the ECHO10 expected distributions for comparing with the distributions in the UMM-C
  record. ECHO10 only has one Distribution, so here we just pick the first one."
  [distributions]
  (some-> distributions
          first
          (assoc :DistributionSize nil :DistributionMedia nil)
          (update-in [:Fees] echo10-expected-fees)
          su/convert-empty-record-to-nil
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
      (assoc :Personnel nil) ;; TODO Implement this as part of CMR-1841
      (assoc :Organizations nil) ;; TODO Implement this as part of CMR-1841
      (update-in [:TemporalExtents] (comp seq (partial take 1)))
      (update-in [:DataDates] fixup-echo10-data-dates)
      (assoc :DataLanguage nil)
      (assoc :Quality nil)
      (assoc :UseConstraints nil)
      (assoc :PublicationReferences nil)
      (assoc :AncillaryKeywords nil)
      (assoc :ISOTopicCategories nil)
      (assoc :Personnel nil)
      (assoc :Organizations nil)
      (update-in [:ProcessingLevel] su/convert-empty-record-to-nil)
      (update-in [:Distributions] echo10-expected-distributions)
      (update-in-each [:SpatialExtent :HorizontalSpatialDomain :Geometry :GPolygons] fix-echo10-polygon)
      (update-in [:SpatialExtent] prune-empty-maps)
      (update-in-each [:AdditionalAttributes] assoc :Group nil :MeasurementResolution nil
                      :ParameterUnitsOfMeasure nil :ParameterValueAccuracy nil
                      :ValueAccuracyExplanation nil :UpdateDate nil)
      (update-in-each [:Projects] assoc :Campaigns nil)
      (update-in [:RelatedUrls] expected-echo10-related-urls)))

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

(defn- expected-dif-instruments
  "Returns the expected DIF instruments for the given instruments"
  [instruments]
  (seq (map #(assoc % :Characteristics nil :Technique nil :NumberOfSensors nil :Sensors nil
                    :OperationalModes nil) instruments)))

(defn- expected-dif-platform
  "Returns the expected DIF platform for the given platform"
  [platform]
  (-> platform
      (assoc :Type nil :Characteristics nil)
      (update-in [:Instruments] expected-dif-instruments)))

(defn- expected-dif-platforms
  "Returns the expected DIF parsed platforms for the given platforms."
  [platforms]
  (let [platforms (seq (map expected-dif-platform platforms))]
    (if (= 1 (count platforms))
      platforms
      (if-let [instruments (seq (mapcat :Instruments platforms))]
        (conj (map #(assoc % :Instruments nil) platforms)
              (cmn/map->PlatformType {:ShortName su/not-provided
                                      :LongName su/not-provided
                                      :Instruments instruments}))
        platforms))))

(defmethod convert-internal :dif
  [umm-coll _]
  (-> umm-coll
      ;; DIF 9 only supports entry-id in metadata associations
      (update-in-each [:MetadataAssociations] assoc :Type nil :Description nil :Version nil)
      ;; DIF 9 does not support tiling identification system
      (assoc :TilingIdentificationSystem nil)
      (assoc :Personnel nil) ;; TODO Implement this as part of CMR-1841
      (assoc :Organizations nil) ;; TODO Implement this as part of CMR-1841
      ;; DIF 9 does not support DataDates
      (assoc :DataDates nil)
      ;; DIF 9 sets the UMM Version to 'Not provided' if it is not present in the DIF 9 XML
      (assoc :Version (or (:Version umm-coll) su/not-provided))
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
      (update-in [:Distributions] su/remove-empty-records)
      ;; DIF 9 does not support Platform Type or Characteristics. The mapping for Instruments is
      ;; unable to be implemented as specified.
      (update-in [:Platforms] expected-dif-platforms)
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

(defn- filter-dif10-metadata-associations
  "Removes metadata associations with type \"LARGER CITATIONS WORKS\" since this type is not
  allowed in DIF10."
  [mas]
  (seq (filter #(not= (:Type %) "LARGER CITATION WORKS")
               mas)))

(defn- fix-dif10-matadata-association-type
  "Defaults metadata association type to \"SCIENCE ASSOCIATED\"."
  [ma]
  (update-in ma [:Type] #(or % "SCIENCE ASSOCIATED")))

(defn- expected-dif10-related-urls
  [related-urls]
  (seq (for [related-url related-urls]
         (assoc related-url :Title nil :Caption nil :FileSize nil :MimeType nil))))

(defmethod convert-internal :dif10
  [umm-coll _]
  (-> umm-coll
      (update-in [:MetadataAssociations] filter-dif10-metadata-associations)
      (update-in-each [:MetadataAssociations] fix-dif10-matadata-association-type)
      (assoc :Personnel nil) ;; TODO Implement this as part of CMR-1841
      (assoc :Organizations nil) ;; TODO Implement this as part of CMR-1841
      (update-in [:SpatialExtent] prune-empty-maps)
      (update-in [:DataDates] fixup-dif10-data-dates)
      (update-in [:Distributions] su/remove-empty-records)
      (update-in-each [:Platforms] dif10-platform)
      (update-in-each [:AdditionalAttributes] assoc :Group nil :UpdateDate nil)
      (update-in [:ProcessingLevel] dif10-processing-level)
      (update-in-each [:Projects] dif10-project)
      (update-in [:PublicationReferences] prune-empty-maps)
      (update-in-each [:PublicationReferences] dif-publication-reference)
      (update-in [:RelatedUrls] expected-dif10-related-urls)
      ;; The following fields are not supported yet
      (assoc :Organizations nil
             :Personnel nil)))

;; ISO 19115-2

(defn propagate-first
  "Returns coll with the first element's value under k assoc'ed to each element in coll.

  Example: (propagate-first :x [{:x 1} {:y 2}]) => [{:x 1} {:x 1 :y 2}]"
  [k coll]
  (let [v (get (first coll) k)]
    (for [x coll]
      (assoc x k v))))

(defn sort-by-date-type-iso
  "Returns temporal extent records to match the order in which they are generated in ISO XML."
  [extents]
  (let [ranges (filter :RangeDateTimes extents)
        singles (filter :SingleDateTimes extents)]
    (seq (concat ranges singles))))

(defn- fixup-iso-ends-at-present
  "Updates temporal extents to be true only when they have both :EndsAtPresentFlag = true AND values
  in RangeDateTimes, otherwise nil."
  [temporal-extents]
  (for [extent temporal-extents]
    (let [ends-at-present (:EndsAtPresentFlag extent)
          rdts (seq (:RangeDateTimes extent))]
      (-> extent
          (update-in-each [:RangeDateTimes]
                          update-in [:EndingDateTime] (fn [x]
                                                        (when-not ends-at-present
                                                          x)))
          (assoc :EndsAtPresentFlag
                 (when (and rdts ends-at-present)
                   true))))))

(defn- fixup-comma-encoded-values
  [temporal-extents]
  (for [extent temporal-extents]
    (update-in extent [:TemporalRangeType] (fn [x]
                                             (when x
                                               (iso/sanitize-value x))))))

(defn expected-iso-19115-2-temporal
  [temporal-extents]
  (->> temporal-extents
       (propagate-first :PrecisionOfSeconds)
       (propagate-first :TemporalRangeType)
       fixup-comma-encoded-values
       fixup-iso-ends-at-present
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

(defn- update-iso-19115-2-related-url-protocol
  "Returns the related url with protocol field updated. Browse urls do not have Protocol field."
  [related-url]
  (if (= "GET RELATED VISUALIZATION" (get-in related-url [:ContentType :Type]))
    (assoc related-url :Protocol nil)
    related-url))

(defn- expected-iso-19115-2-related-urls
  [related-urls]
  (seq (for [related-url related-urls
             url (:URLs related-url)]
         (-> related-url
             (assoc :Title nil :MimeType nil :Caption nil :FileSize nil :URLs [url])
             (assoc-in [:ContentType :Subtype] nil)
             (update-in [:ContentType]
                        (fn [content-type]
                          (when (#{"GET DATA"
                                   "GET RELATED VISUALIZATION"
                                   "VIEW RELATED INFORMATION"} (:Type content-type))
                            content-type)))
             update-iso-19115-2-related-url-protocol))))

(defn- fix-iso-vertical-spatial-domain-values
  [vsd]
  (let [fix-val (fn [x]
                  (when x
                    ;; Vertical spatial domain values are encoded in a comma-separated string in ISO
                    ;; XML, so the values must be updated to match what we expect in the resulting
                    ;; XML document.
                    (iso/sanitize-value x)))]
    (-> vsd
        (update-in [:Type] fix-val)
        (update-in [:Value] fix-val))))

(defn update-iso-spatial
  [spatial-extent]
  (-> spatial-extent
      (assoc-in [:HorizontalSpatialDomain :ZoneIdentifier] nil)
      (update-in-each [:HorizontalSpatialDomain :Geometry :BoundingRectangles] assoc :CenterPoint nil)
      (update-in-each [:HorizontalSpatialDomain :Geometry :Lines] assoc :CenterPoint nil)
      (update-in-each [:HorizontalSpatialDomain :Geometry :GPolygons] assoc :CenterPoint nil)
      (update-in [:VerticalSpatialDomains] #(take 1 %))
      (update-in-each [:VerticalSpatialDomains] fix-iso-vertical-spatial-domain-values)
      prune-empty-maps))

(defn- expected-person
  [person]
  (when-let [{:keys [FirstName MiddleName LastName]} person]
    (-> person
        (assoc :Uuid nil :FirstName nil :MiddleName nil)
        (assoc :LastName (str/join
                           " " (remove nil? [FirstName MiddleName LastName]))))))

(defn- expected-contacts
  "Returns the contacts with type phone or email"
  [contacts]
  (seq (filter #(.contains #{"phone" "email"} (:Type %)) contacts)))

(defn- update-with-expected-party
  "Update the given organization or personnel with expected person in the party"
  [party]
  (-> party
      (update-in [:Party :Person] expected-person)
      (update-in [:Party :Contacts] expected-contacts)
      (update-in [:Party :OrganizationName] (fn [org-name]
                                              (when org-name
                                                (assoc org-name :LongName nil :Uuid nil))))
      (update-in [:Party :Addresses] (fn [x]
                                       (when-let [address (first x)]
                                         [address])))
      (update-in [:Party :RelatedUrls] (fn [x]
                                         (when-let [related-url (first x)]
                                           (-> related-url
                                               (assoc :Protocol nil :Title nil
                                                      :FileSize nil :ContentType nil
                                                      :MimeType nil :Caption nil)
                                               (update-in [:URLs] (fn [urls] [(first urls)]))
                                               vector))))))

(defn- expected-responsibility
  [responsibility]
  (-> responsibility
      (update-in [:Party :RelatedUrls] (fn [urls]
                                         (seq (map #(assoc % :ContentType nil) urls))))
      update-with-expected-party))

(defn- expected-responsibilities
  [responsibilities allowed-roles]
  (let [resp-by-role (group-by :Role responsibilities)
        resp-by-role (update-in resp-by-role ["DISTRIBUTOR"] #(take 1 %))]
    (seq (map expected-responsibility
              (mapcat resp-by-role allowed-roles)))))

(defn- group-metadata-assocations
  [mas]
  (let [{input-types true other-types false} (group-by (fn [ma] (= "INPUT" (:Type ma))) mas)]
    (seq (concat other-types input-types))))

(defmethod convert-internal :iso19115
  [umm-coll _]
  (-> umm-coll
      (update-in [:SpatialExtent] update-iso-spatial)
      (update-in [:TemporalExtents] expected-iso-19115-2-temporal)
      ;; The following platform instrument properties are not supported in ISO 19115-2
      (update-in-each [:Platforms] update-in-each [:Instruments] assoc
                      :NumberOfSensors nil
                      :OperationalModes nil)
      (assoc :CollectionDataType nil)
      (update-in [:DataLanguage] #(or % "eng"))
      (update-in [:ProcessingLevel] su/convert-empty-record-to-nil)
      (update-in [:Distributions] expected-iso-19115-2-distributions)
      (update-in-each [:Projects] assoc :Campaigns nil :StartDate nil :EndDate nil)
      (update-in [:PublicationReferences] iso-19115-2-publication-reference)
      (update-in [:RelatedUrls] expected-iso-19115-2-related-urls)
      (update-in-each [:AdditionalAttributes] assoc :UpdateDate nil)
      (update-in [:Personnel]
                 expected-responsibilities ["POINTOFCONTACT"])
      (update-in [:Organizations]
                 expected-responsibilities ["POINTOFCONTACT" "ORIGINATOR" "DISTRIBUTOR" "PROCESSOR"])
      (update-in [:MetadataAssociations] group-metadata-assocations)))

;; ISO-SMAP
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
        (assoc :SpatialCoverageType "GEODETIC" :GranuleSpatialRepresentation "GEODETIC")
        (assoc :VerticalSpatialDomains nil :OrbitParameters nil)
        (assoc-in [:HorizontalSpatialDomain :ZoneIdentifier] nil)
        (update-in [:HorizontalSpatialDomain :Geometry]
                   assoc :CoordinateSystem "GEODETIC" :Points nil :GPolygons nil :Lines nil)
        (update-in-each [:HorizontalSpatialDomain :Geometry :BoundingRectangles] assoc :CenterPoint nil)
        prune-empty-maps)))

(defn- fixup-smap-data-dates
  "Because when we generate our bogus SMAP data for round-trip tests, we duplicate the first date
  value in DataDates."
  [data-dates]
  (cons (first data-dates)
        data-dates))

(defmethod convert-internal :iso-smap
  [umm-coll _]
  (-> umm-coll
      ;; TODO - Implement this as part of CMR-2058
      (update-in-each [:TemporalExtents] assoc :EndsAtPresentFlag nil)
      (convert-internal :iso19115)
      (update-in [:SpatialExtent] expected-smap-iso-spatial-extent)
      (update-in [:DataDates] fixup-smap-data-dates)
      ;; ISO SMAP does not support the PrecisionOfSeconds field.
      (update-in-each [:TemporalExtents] assoc :PrecisionOfSeconds nil)
      ;; TODO - Implement this as part of CMR-2057
      (update-in-each [:TemporalExtents] assoc :TemporalRangeType nil)
      ;; TODO - Implement this as part of CMR-1946
      (assoc :Quality nil)
      ;; Fields not supported by ISO-SMAP
      (assoc :MetadataAssociations nil) ;; Not supported for ISO SMAP
      (assoc :Personnel nil) ;; TODO Implement this as part of CMR-1841
      (assoc :Organizations nil) ;; TODO Implement this as part of CMR-1841
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
      (update-in [:Platforms] normalize-smap-instruments)))

;;; Unimplemented Fields

(def not-implemented-fields
  "This is a list of required but not implemented fields."
  #{:CollectionCitations :MetadataDates :MetadataLanguage
    :DirectoryNames :MetadataLineages :SpatialInformation
    :PaleoTemporalCoverage})

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
