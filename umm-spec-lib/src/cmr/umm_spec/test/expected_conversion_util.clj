(ns cmr.umm-spec.test.expected-conversion-util
 "Common functionality for expected conversions"
 (:require
  [clj-time.core :as t]
  [clj-time.format :as f]
  [clojure.string :as string]
  [cmr.common-app.services.kms-fetcher :as kf]
  [cmr.common.util :as util :refer [update-in-each]]
  [cmr.umm-spec.dif-util :as dif-util]
  [cmr.umm-spec.location-keywords :as lk]
  [cmr.umm-spec.models.umm-collection-models :as umm-c]
  [cmr.umm-spec.models.umm-common-models :as cmn]
  [cmr.umm-spec.test.location-keywords-helper :as lkt]
  [cmr.umm-spec.url :as url]
  [cmr.umm-spec.util :as su]))

(def coll-progress-enum-list
  "The enum list for CollectionProgress in v1.10. that could be converted from
   all formats except for DIF10"
  (set ["PLANNED" "ACTIVE" "COMPLETE" "NOT PROVIDED" "NOT APPLICABLE"]))

(defn expected-coll-progress
  "Returns the expected colleciton progress after converting from umm to other format, then back to umm."
  ([umm-coll]
   (expected-coll-progress umm-coll coll-progress-enum-list))
  ([umm-coll enum-list-set]
   (let [coll-progress (:CollectionProgress umm-coll)
         upper-case-coll-progress (when coll-progress
                                    (string/upper-case coll-progress))]
     (if (coll-progress-enum-list upper-case-coll-progress)
       upper-case-coll-progress
       "NOT PROVIDED"))))

(def relation-set #{"GET DATA"
                    "GET RELATED VISUALIZATION"
                    "VIEW RELATED INFORMATION"})

(defn prune-empty-maps
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

(defn fixup-dif10-data-dates
  "Returns DataDates seq as it would be parsed from ECHO and DIF 10 XML document."
  [data-dates]
  (when (seq data-dates)
    (let [date-types (group-by :Type data-dates)]
      (filter some?
              (for [date-type ["CREATE" "UPDATE" "REVIEW" "DELETE"]]
                (last (sort-by :Date (get date-types date-type))))))))

(defn create-date-type
  "Create a DateType from a date-time. Note that time will dropped"
  [date-time type]
  (when (some? date-time)
    (cmn/map->DateType {:Date (f/unparse (f/formatters :date) date-time)
                        :Type type})))

(defn date-time->date
  "Returns the given datetime to a date."
  [date-time]
  (some->> date-time
           (f/unparse (f/formatters :date))
           (f/parse (f/formatters :date))))

(defn geometry-with-coordinate-system
  "Returns the geometry with default CoordinateSystem added if it doesn't have a CoordinateSystem."
  [geometry]
  (when geometry
    (update-in geometry [:CoordinateSystem] #(if % % "CARTESIAN"))))

(defn fix-echo10-dif10-polygon
  "Because the generated points may not be in valid UMM order (closed and CCW), we need to do some
  fudging here."
  [gpolygon]
  (let [fix-points (fn [points]
                     (-> points
                         su/closed-counter-clockwise->open-clockwise
                         su/open-clockwise->closed-counter-clockwise))]
    (-> gpolygon
        (update-in [:Boundary :Points] fix-points)
        (update-in-each [:ExclusiveZone :Boundaries] update-in [:Points] fix-points))))

(defn fix-location-keyword-conversion
  "Takes a non-kms keyword and converts it to the expected value"
  [location-keywords]
  ;;Convert the Location Keyword to a leaf.
  (let [leaf-values (lk/location-keywords->spatial-keywords location-keywords)
        translated-values (lk/translate-spatial-keywords
                           (kf/get-kms-index (lkt/setup-context-for-test)) leaf-values)]
    ;;If the keyword exists in the hierarchy
    (seq (map #(umm-c/map->LocationKeywordType %) translated-values))))

(defn expected-dif-addresses
  "Returns the expected DIF addresses"
  [addresses]
  (when (seq addresses)
    [(first addresses)]))

(defn dif-online-resource
  "Sanitize the URL and dissoc unmapped fields"
  [online-resource]
  (cmn/map->OnlineResourceType
   (select-keys (update online-resource :Linkage #(url/format-url % true)) [:Linkage])))

(defn- check-nil-pub-ref
 "If the online resource name and description are the only fields in the publication
 reference, return nil pub ref. They are hardcoded not mapped and give problems with the
 expected conversion when translating back and forth between umm and xml"
 [pub-ref]
 (let [pub-ref (util/remove-nil-keys pub-ref)]
  (if (= (keys pub-ref) [:OnlineResource])
   (when (get-in pub-ref [:OnlineResource :Linkage])
    pub-ref)
   pub-ref)))

(defn expected-dif-url-type
 "Perform a roundtrip of the URLContentType, Type, and Subtype to get the values back.
 Returns {:URLContentType 'X' :Type 'Y' :Subtype 'Z'}"
 [related-url]
 (let [url-type (util/remove-nil-keys
                 (select-keys related-url [:URLContentType :Type :Subtype]))
       dif-content-type (dif-util/umm-url-type->dif-umm-content-type url-type)]
   (get dif-util/dif-url-content-type->umm-url-types dif-content-type su/default-url-type)))

(defn expected-related-urls-for-dif
  "Expected Related URLs for DIF concepts"
  [related-urls]
  (when (seq related-urls)
    (seq (for [related-url related-urls
               :let [url-type (expected-dif-url-type related-url)]]
           (cmn/map->RelatedUrlType
            (merge
             url-type
             (-> related-url
                 (update-in [:URL] #(url/format-url % true))
                 (dissoc :URLContentType :Type :Subtype :Relation :FileSize :MimeType :GetData :GetService))))))))

(def bounding-rectangles-path
  "The path in UMM to bounding rectangles."
  [:SpatialExtent :HorizontalSpatialDomain :Geometry :BoundingRectangles])

(defn expected-access-constraints
  "Return access constraints with default Description if applicable"
  [access-constraints]
  (when (some? access-constraints)
    (update access-constraints :Description su/with-default)))

(defn expected-related-url
  "Format the URL in RelatedUrl"
  [entry]
  (if-let [url (get-in entry [:RelatedUrl :URL])]
    (assoc-in entry [:RelatedUrl :URL]
      (url/format-url url true))
    entry))

(defn- expected-related-urls
  "Format all of the URLs in RelatedUrls, returns a list of RelatedUrls"
  [related-urls url-content-type]
  (seq (for [ru related-urls]
         (-> ru
             (assoc :URL (url/format-url (:URL ru) true))
             (assoc :URLContentType url-content-type)
             (assoc :Type "HOME PAGE")
             (dissoc :Subtype)))))

(defn expected-contact-information-urls
  "Format all of the URLs in ContactInformation"
  [records url-content-type]
  (seq (for [record records]
         (if (and (some? (:ContactInformation record))
                  (seq (:RelatedUrls (:ContactInformation record))))
           (update-in record [:ContactInformation :RelatedUrls]
             #(expected-related-urls % url-content-type))
           record))))

(defn expected-data-center-urls
  "Format all of the URLs in Data Centers, including the data center contact info,
  the contact groups contact info, and the contact persons contact info"
  [data-centers]
  (def data-centers data-centers)
  (seq (for [dc (expected-contact-information-urls data-centers "DataCenterURL")]
         (-> dc
             (update :ContactPersons expected-contact-information-urls "DataContactURL")
             (update :ContactGroups expected-contact-information-urls "DataContactURL")))))

(defn dif-expected-data-language
  "DIF and DIF10 do conversions on data language, since they only accept certain formats of
  language so convert the language to dif and back"
  [language]
  (when language
    (let [dif-language (dif-util/umm-language->dif-language language)]
      (dif-util/dif-language->umm-language dif-language))))

(defn expected-dif-doi
  "DIF9 and DIF10 do not have several DOI fields so remove them."
  [doi]
  (let [updated-doi (util/remove-nil-keys
                     (dissoc doi :Authority :MissingReason :Explanation))]
    (if (seq updated-doi)
      (cmn/map->DoiDoiType updated-doi)
      {:Explanation "It is unknown if this record has a DOI.",
       :MissingReason "Unknown"})))

(defn expected-dif-pub-doi
  "DIF9 and DIF10 do not have several DOI fields so remove them."
  [doi]
  (let [updated-doi (util/remove-nil-keys
                     (dissoc doi :Authority :MissingReason :Explanation))]
    (when (seq updated-doi)
      (util/remove-nil-keys
        (cmn/map->DoiDoiType updated-doi)))))

(defn dif-publication-reference
  "Returns the expected value of a parsed DIF 9 or DIF10 publication reference"
  [pub-ref]
  (-> pub-ref
      (update-in [:DOI] expected-dif-pub-doi)
      (update :ISBN su/format-isbn)
      (as-> pr (if (:OnlineResource pr)
                 (update pr :OnlineResource dif-online-resource)
                 pr))
      check-nil-pub-ref))
