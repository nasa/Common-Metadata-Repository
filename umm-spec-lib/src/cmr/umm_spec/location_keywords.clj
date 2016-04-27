(ns cmr.umm-spec.location-keywords
  (:require [cmr.umm-spec.models.collection :as c]
            [cmr.common-app.services.kms-fetcher :as kf]))

(def duplicate-keywords
  "Temporary lookup table to account for any duplicate keywords. Key is :uuid which is a field in
  the location-keyword map. "
  {"BLACK SEA" {:uuid "afbc0a01-742e-49da-939e-3eaa3cf431b0"}
   "SPACE" {:uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"}})

(def local-test-system-context
  {:system (get-in user/system [:apps :indexer])})

(defn gcmd-spatial-keywords-map
  [context]
  "Returns a list of maps that correspond to the spatial keywords hierarchy of GCMD keywords.
  Keys are :category :type :subregion-1 :subregion-2 :subregion-3 :uuid. Values are single string-type.
  Not all keys are present in every map."
  (vals (:spatial-keywords (kf/get-gcmd-keywords-map context))))

(defn find-spatial-keyword-in-map
  "Finds a spatial keyword in the gcmd spatial-keywords map.
  Takes a string keyword as a parameter, e.g. 'OCEAN' and returns a list of maps of hierarchies
  which contain the keyword."
  [context keyword]
  (filter (fn [map] (some #{keyword} (vals map))) (gcmd-spatial-keywords-map context)))

(defn find-spatial-keyword
  "Finds spatial keywords in the heirarchy and pick the one with the fewest keys
  (e.g. highest hierarchical depth.) Takes a string keyword as a parameter and returns the map of
  hierarichies which contain the keyword. You can also look by entering the uuid of the entry
  if you know it."
  [context keyword]
  (first (sort-by count (find-spatial-keyword-in-map context keyword))))

;; Example
(comment
  (find-spatial-keyword local-test-system-context "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f")
 )
