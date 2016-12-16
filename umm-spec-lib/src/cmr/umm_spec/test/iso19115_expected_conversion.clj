(ns cmr.umm-spec.test.iso19115-expected-conversion
  "ISO 19115 specific expected conversion functionality"
  (:require
    [clj-time.core :as t]
    [clj-time.format :as f]
    [clojure.string :as str]
    [cmr.common.util :as util :refer [update-in-each]]
    [cmr.spatial.mbr :as m]
    [cmr.umm-spec.date-util :as date-util]
    [cmr.umm-spec.iso19115-2-util :as iso-util]
    [cmr.umm-spec.json-schema :as js]
    [cmr.umm-spec.location-keywords :as lk]
    [cmr.umm-spec.models.umm-collection-models :as umm-c]
    [cmr.umm-spec.models.umm-common-models :as cmn]
    [cmr.umm-spec.related-url :as ru-gen]
    [cmr.umm-spec.test.expected-conversion-util :as conversion-util]
    [cmr.umm-spec.test.location-keywords-helper :as lkt]
    [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.additional-attribute :as iso-aa]
    [cmr.umm-spec.umm-to-xml-mappings.iso19115-2 :as iso]
    [cmr.umm-spec.url :as url]
    [cmr.umm-spec.util :as su]))

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

(defn- propagate-first
  "Returns coll with the first element's value under k assoc'ed to each element in coll.

  Example: (propagate-first :x [{:x 1} {:y 2}]) => [{:x 1} {:x 1 :y 2}]"
  [k coll]
  (let [v (get (first coll) k)]
    (for [x coll]
      (assoc x k v))))

(defn- sort-by-date-type-iso
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
                 (boolean (and rdts ends-at-present)))))))

(defn- fixup-comma-encoded-values
  [temporal-extents]
  (for [extent temporal-extents]
    (update-in extent [:TemporalRangeType] (fn [x]
                                             (when x
                                               (str/trim (iso-util/sanitize-value x)))))))

(defn expected-iso-19115-2-temporal
  [temporal-extents]
  (->> temporal-extents
       (propagate-first :PrecisionOfSeconds)
       (propagate-first :TemporalRangeType)
       fixup-comma-encoded-values
       fixup-iso-ends-at-present
       (split-temporals :RangeDateTimes)
       (split-temporals :SingleDateTimes)
       sort-by-date-type-iso
       (#(or (seq %) su/not-provided-temporal-extents))))

(defn- iso-19115-2-publication-reference
  "Returns the expected value of a parsed ISO-19115-2 publication references"
  [pub-refs]
  (seq (for [pub-ref pub-refs
             :when (and (:Title pub-ref) (:PublicationDate pub-ref))]
         (-> pub-ref
             (assoc :ReportNumber nil :Volume nil :RelatedUrl nil :PublicationPlace nil)
             (update-in [:DOI] (fn [doi] (when doi (assoc doi :Authority nil))))
             (update-in [:PublicationDate] conversion-util/date-time->date)
             (update :ISBN su/format-isbn)))))

(defn- expected-iso-19115-2-distributions
  "Returns the expected ISO19115-2 distributions for comparison."
  [distributions]
  (some->> distributions
           su/remove-empty-records
           vec))

(defn- expected-iso-19115-2-related-urls
  [related-urls]
  (if (seq related-urls)
    (seq (for [related-url related-urls
               url (:URLs related-url)]
           (-> related-url
               (assoc :Title nil :MimeType nil :FileSize nil :URLs [url])
               (update-in [:Relation]
                          (fn [[rel]]
                            (when (conversion-util/relation-set rel)
                              [rel])))
               (update-in-each [:URLs] #(url/format-url % true)))))
    [su/not-provided-related-url]))

(defn- fix-iso-vertical-spatial-domain-values
  [vsd]
  (let [fix-val (fn [x]
                  (when x
                    ;; Vertical spatial domain values are encoded in a comma-separated string in ISO
                    ;; XML, so the values must be updated to match what we expect in the resulting
                    ;; XML document.
                    (str/trim (iso-util/sanitize-value x))))]
    (-> vsd
        (update-in [:Type] fix-val)
        (update-in [:Value] fix-val))))

(defn- update-iso-spatial
  [spatial-extent]
  (-> spatial-extent
      (assoc-in [:HorizontalSpatialDomain :ZoneIdentifier] nil)
      (update-in-each [:HorizontalSpatialDomain :Geometry :BoundingRectangles] assoc :CenterPoint nil)
      (update-in-each [:HorizontalSpatialDomain :Geometry :Lines] assoc :CenterPoint nil)
      (update-in-each [:HorizontalSpatialDomain :Geometry :GPolygons] assoc :CenterPoint nil)
      (update-in [:VerticalSpatialDomains] #(take 1 %))
      (update-in-each [:VerticalSpatialDomains] fix-iso-vertical-spatial-domain-values)
      conversion-util/prune-empty-maps))

(defn- group-metadata-assocations
  [mas]
  (let [{input-types true other-types false} (group-by (fn [ma] (= "INPUT" (:Type ma))) mas)]
    (seq (concat other-types input-types))))

(defn- update-iso-topic-categories
  "Update ISOTopicCategories values to a default value if it's not one of the specified values."
  [categories]
  (seq (map iso/iso-topic-value->sanitized-iso-topic-category categories)))

(defn- normalize-bounding-rectangle
  [{:keys [WestBoundingCoordinate NorthBoundingCoordinate
           EastBoundingCoordinate SouthBoundingCoordinate
           CenterPoint]}]
  (let [{:keys [west north east south]} (m/mbr WestBoundingCoordinate
                                               NorthBoundingCoordinate
                                               EastBoundingCoordinate
                                               SouthBoundingCoordinate)]
    (cmn/map->BoundingRectangleType
      {:CenterPoint CenterPoint
       :WestBoundingCoordinate west
       :NorthBoundingCoordinate north
       :EastBoundingCoordinate east
       :SouthBoundingCoordinate south})))

(defn- fix-bounding-rectangles
  "Bounding rectangles in UMM JSON during conversion will be passed to the MBR namespace which does
  some normalization on them. The result is still the same area but the values will not be identical."
  [umm]
  (if-let [brs (seq (get-in umm conversion-util/bounding-rectangles-path))]
    (assoc-in umm conversion-util/bounding-rectangles-path (mapv normalize-bounding-rectangle brs))
    umm))

(defn- geom->bounding-rectangle
  "Create a rectangle from a line or polygon"
  [boundary]
  (when-let [points (:Points boundary)]
   (let [lats (map :Latitude points)
         lons (map :Longitude points)]
     (cmn/map->BoundingRectangleType
      {:WestBoundingCoordinate (apply min lons)
       :NorthBoundingCoordinate (apply max lats)
       :EastBoundingCoordinate (apply max lons)
       :SouthBoundingCoordinate (apply min lats)}))))

(defn- point->bounding-rectangle
  "Create a bounding rectangle from a point. Take into account special bounding box for the poles."
  [point]
  (let [{:keys [Latitude Longitude]} point
        pole? (or (= Latitude 90.0) (= Latitude -90.0))]
   (cmn/map->BoundingRectangleType
     {:WestBoundingCoordinate (if pole?
                                -180.0
                                Longitude)
      :NorthBoundingCoordinate Latitude
      :EastBoundingCoordinate (if pole?
                                180.0
                                Longitude)
      :SouthBoundingCoordinate Latitude})))

(defn- get-bounding-rectangles-for-geometry
  "Get a bounding rectangle for each polygon, line, point."
  [umm]
  (let [geometry (get-in umm [:SpatialExtent :HorizontalSpatialDomain :Geometry])]
    (concat (map point->bounding-rectangle (:Points geometry))
            (map geom->bounding-rectangle (concat (map :Boundary (:GPolygons geometry))
                                                (:Lines geometry))))))

(defn- update-bounding-rectangles
  "Update bounding rectangles to mimic what ISO does. For each geometry (polygon, line, point),
   create a bounding box. For each bounding box, a duplicate is created."
  [umm]
  (let [geom-rects (get-bounding-rectangles-for-geometry umm)
        bounding-rects (get-in umm conversion-util/bounding-rectangles-path)
        bounding-rects (when (and (seq bounding-rects))
                         (interleave bounding-rects bounding-rects))]
    (-> umm
        (assoc-in conversion-util/bounding-rectangles-path (concat geom-rects bounding-rects))
        fix-bounding-rectangles)))

(defn- expected-iso19115-additional-attribute
  [attribute]
  (-> attribute
      (assoc :UpdateDate nil)
      (assoc :Description (su/with-default (:Description attribute)))))

(defn- expected-iso19115-additional-attributes
  "Update each additional attribute and re-order the additional attributes to be non data Quality
  then data quality additional attributes"
  [additional-attributes]
  (let [aas (map expected-iso19115-additional-attribute additional-attributes)]
    (concat
      (seq (remove #(iso-aa/data-quality-info-attributes (:Name %)) aas))
      (seq (filter #(iso-aa/data-quality-info-attributes (:Name %)) aas)))))

(defn- expected-iso19115-data-dates
  "Returns the expected ISO19115 DataDates"
  [data-dates]
  (if data-dates
    data-dates
    [(cmn/map->DateType {:Date (f/parse date-util/default-date-value)
                         :Type "CREATE"})]))

(defn- expected-science-keywords
  "Returns science keywords if not nil, otherwise default"
  [science-keywords]
  (if (seq science-keywords)
    science-keywords
    su/not-provided-science-keywords))

(defn umm-expected-conversion-iso19115
  [umm-coll]
  (-> umm-coll
      (assoc :DirectoryNames nil)
      update-bounding-rectangles
      (update :SpatialExtent update-iso-spatial)
      ;; ISO only supports a single tiling identification system
      (update :TilingIdentificationSystems #(seq (take 1 %)))
      (update :TemporalExtents expected-iso-19115-2-temporal)
      ;; The following platform instrument properties are not supported in ISO 19115-2
      (update-in-each [:Platforms] update-in-each [:Instruments] assoc
                      :NumberOfSensors nil
                      :OperationalModes nil)
      (update :DataDates expected-iso19115-data-dates)
      (assoc :CollectionDataType nil)
      (update :DataLanguage #(or % "eng"))
      (update :ProcessingLevel su/convert-empty-record-to-nil)
      (update :Distributions expected-iso-19115-2-distributions)
      (update-in-each [:Projects] assoc :Campaigns nil :StartDate nil :EndDate nil)
      (update :PublicationReferences iso-19115-2-publication-reference)
      (update :RelatedUrls expected-iso-19115-2-related-urls)
      (update :AdditionalAttributes expected-iso19115-additional-attributes)
      (update :MetadataAssociations group-metadata-assocations)
      (update :ISOTopicCategories update-iso-topic-categories)
      (update :LocationKeywords conversion-util/fix-location-keyword-conversion)
      (assoc :SpatialKeywords nil)
      (assoc :PaleoTemporalCoverages nil)
      (assoc :DataCenters [su/not-provided-data-center])
      (assoc :ContactGroups nil)
      (assoc :ContactPersons nil)
      (assoc :MetadataDates nil)
      (update :ScienceKeywords expected-science-keywords)
      (update :AccessConstraints conversion-util/expected-access-constraints)
      (update :CollectionProgress su/with-default)
      js/parse-umm-c))
