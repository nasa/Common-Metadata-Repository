(ns cmr.umm-spec.test.echo10-expected-conversion
 "ECHO 10 specific expected conversion functionality"
 (:require [clj-time.core :as t]
           [clj-time.format :as f]
           [cmr.umm-spec.util :as su]
           [cmr.common.util :as util :refer [update-in-each]]
           [cmr.umm-spec.test.expected-conversion-util :as conversion-util]
           [cmr.umm-spec.related-url :as ru-gen]
           [cmr.umm-spec.location-keywords :as lk]
           [cmr.umm-spec.test.location-keywords-helper :as lkt]
           [cmr.umm-spec.models.collection :as umm-c]))


(defn- fixup-echo10-data-dates
  [data-dates]
  (seq
    (remove #(= "REVIEW" (:Type %))
            (conversion-util/fixup-dif10-data-dates data-dates))))

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
          (assoc :Sizes nil :DistributionMedia nil)
          (update-in [:Fees] echo10-expected-fees)
          su/convert-empty-record-to-nil
          vector))

(defn- expected-echo10-related-urls
  [related-urls]
  (seq (for [related-url related-urls
             :let [[rel] (:Relation related-url)]
             url (:URLs related-url)]
         (-> related-url
             (assoc :Title nil :URLs [url])
             (update-in [:FileSize] (fn [file-size]
                                      (when (and file-size
                                                 (= rel "GET RELATED VISUALIZATION"))
                                        (when-let [byte-size (ru-gen/convert-to-bytes
                                                               (:Size file-size) (:Unit file-size))]
                                          (assoc file-size :Size (/ (int byte-size) 1024) :Unit "KB")))))
             (update-in [:Relation] (fn [[rel]]
                                      (when (conversion-util/relation-set rel)
                                        [rel])))))))

(defn- expected-echo10-spatial-extent
  "Returns the expected ECHO10 SpatialExtent for comparison with the umm model."
  [spatial-extent]
  (let [spatial-extent (conversion-util/prune-empty-maps spatial-extent)]
    (if (get-in spatial-extent [:HorizontalSpatialDomain :Geometry])
      (update-in spatial-extent
                 [:HorizontalSpatialDomain :Geometry]
                 conversion-util/geometry-with-coordinate-system)
      spatial-extent)))

(defn- expected-echo10-additional-attribute
  [attribute]
  (-> attribute
      (assoc :Group nil)
      (assoc :UpdateDate nil)
      (assoc :MeasurementResolution nil)
      (assoc :ParameterUnitsOfMeasure nil)
      (assoc :ParameterValueAccuracy nil)
      (assoc :ValueAccuracyExplanation nil)
      (assoc :Description (su/with-default (:Description attribute)))))

(defn umm-expected-conversion-echo10
  [umm-coll]
  (-> umm-coll
      (update-in [:TemporalExtents] (comp seq (partial take 1)))
      (update-in [:DataDates] fixup-echo10-data-dates)
      (assoc :DataLanguage nil)
      (assoc :Quality nil)
      (assoc :UseConstraints nil)
      (assoc :PublicationReferences nil)
      (assoc :AncillaryKeywords nil)
      (assoc :ISOTopicCategories nil)
      (assoc :DataCenters [su/not-provided-data-center])
      (assoc :ContactGroups nil)
      (assoc :ContactPersons nil)
      (update-in [:ProcessingLevel] su/convert-empty-record-to-nil)
      (update-in [:Distributions] echo10-expected-distributions)
      (update-in-each [:SpatialExtent :HorizontalSpatialDomain :Geometry :GPolygons]
                      conversion-util/fix-echo10-dif10-polygon)
      (update-in [:SpatialExtent] expected-echo10-spatial-extent)
      (update-in-each [:AdditionalAttributes] expected-echo10-additional-attribute)
      (update-in-each [:Projects] assoc :Campaigns nil)
      (update-in [:RelatedUrls] expected-echo10-related-urls)
      ;; We can't restore Detailed Location because it doesn't exist in the hierarchy.
      (update-in [:LocationKeywords] conversion-util/fix-location-keyword-conversion)
      ;; CMR 2716 Getting rid of SpatialKeywords but keeping them for legacy purposes.
      (assoc :SpatialKeywords nil)
      (assoc :PaleoTemporalCoverages nil)))
