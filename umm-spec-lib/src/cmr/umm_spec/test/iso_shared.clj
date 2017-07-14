(ns cmr.umm-spec.test.iso-shared
  "ISO 19115 specific expected conversion functionality"
  (:require
    [clojure.string :as string]
    [cmr.common.util :as util :refer [update-in-each]]
    [cmr.umm-spec.iso19115-2-util :as iso-util]
    [cmr.umm-spec.models.umm-collection-models :as umm-c]
    [cmr.umm-spec.models.umm-common-models :as cmn]
    [cmr.umm-spec.util :as su]
    [cmr.umm-spec.xml-to-umm-mappings.iso-shared.iso-topic-categories :as iso-topic-categories])
  (:use
   [cmr.umm-spec.models.umm-collection-models]
   [cmr.umm-spec.models.umm-common-models]))

(defn split-temporals
  "Returns a seq of temporal extents with a new extent for each value under key
  k (e.g. :RangeDateTimes) in each source temporal extent."
  [k temporal-extents]
  (reduce (fn [result extent]
            (if-let [values (get extent k)]
              (concat result (map #(assoc extent k [%])
                                  values))
              (conj (vec result) extent)))
          []
          temporal-extents))

(defn sort-by-date-type-iso
  "Returns temporal extent records to match the order in which they are generated in ISO XML."
  [extents]
  (let [ranges (filter :RangeDateTimes extents)
        singles (filter :SingleDateTimes extents)]
    (seq (concat ranges singles))))

(defn fixup-iso-ends-at-present
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

(defn expected-iso-topic-categories
  "Update ISOTopicCategories values to a default value if it's not one of the specified values."
  [categories]
  (->> categories
       (map iso-topic-categories/umm->xml-iso-topic-category-map)
       (map iso-topic-categories/xml->umm-iso-topic-category-map)
       (remove nil?)
       seq))
