(ns cmr.umm-spec.test.expected-conversion-util
 "Common functionality for expected conversions"
 (:require [clj-time.core :as t]
           [clj-time.format :as f]
           [cmr.umm-spec.util :as su]
           [cmr.common.util :as util :refer [update-in-each]]
           [cmr.umm-spec.models.umm-common-models :as cmn]
           [cmr.umm-spec.location-keywords :as lk]
           [cmr.umm-spec.test.location-keywords-helper :as lkt]
           [cmr.umm-spec.models.umm-collection-models :as umm-c]))


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
                            (lkt/setup-context-for-test lkt/sample-keyword-map) leaf-values)]
    ;;If the keyword exists in the hierarchy
    (seq (map #(umm-c/map->LocationKeywordType %) translated-values))))

(defn expected-dif-addresses
  "Returns the expected DIF addresses"
  [addresses]
  (when (seq addresses)
    [(first addresses)]))

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
                                            :Relation nil
                                            :Title nil
                                            :MimeType nil
                                            :FileSize nil))))))


(defn expected-related-urls-for-dif-serf
  "Expected Related URLs for DIF and SERF concepts"
  [related-urls]
  (seq (for [related-url related-urls]
         (assoc related-url :Title nil :FileSize nil :MimeType nil))))

(def bounding-rectangles-path
  "The path in UMM to bounding rectangles."
  [:SpatialExtent :HorizontalSpatialDomain :Geometry :BoundingRectangles])
