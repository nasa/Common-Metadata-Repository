(ns cmr.umm-spec.location-keywords
  "Helper utilities for converting Keywords -> UMM LocationKeywords"
  (:require [cmr.common-app.services.kms-fetcher :as kf]
            [cmr.umm-spec.models.collection :as umm-c]
            [clojure.set :as set]))

(def duplicate-keywords
  "Lookup table to account for any duplicate keywords. Will choose the preferred value.
  Common key is :uuid which is a field in the location-keyword map. "
   ;; Choose Black Sea here because it's more associated with Eastern Europe than Western Asia.
  {"BLACK SEA" {:uuid "afbc0a01-742e-49da-939e-3eaa3cf431b0"}
   ;; Choose a more specific SPACE element because the general SPACE is too broad and top-level.
   "SPACE" {:uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"}
   ;; Choose Georgia the country instead of Georgia the US State.
   "GEORGIA" {:uuid "d79e134c-a4d0-44f2-9706-cad2b59de992"}})

(defn get-spatial-keywords-maps
  "Takes a context map and returns a list of maps that correspond to the spatial keywords hierarchy of the full map of GCMD keywords.
    Keys are :category :type :subregion-1 :subregion-2 :subregion-3 :uuid. Values are single string-type.
    Not all keys are present in every map."
  [context]
  (:spatial-keywords (kf/get-gcmd-keywords-map context)))

(defn find-spatial-keywords-in-map
  "Finds all occurrences of a spatial keyword in the spatial-keywords map.
  Takes a string keyword as a parameter, e.g. 'OCEAN' and a list of spatial keyword maps;
  returns a list of maps of hierarchies which contain the keyword."
  [keyword-map-list keyword]
  (proto/save 2)
  (filter (fn [map] (some #{keyword} (vals map))) (vals keyword-map-list)))

(defn find-spatial-keyword
  "Finds spatial keywords in the heirarchy and pick the one with the fewest keys
  (e.g. shortest hierarchical depth.) Takes a string keyword as a parameter (assumes no case) a list
  of keyword maps and returns the map of hierarichies which contain the keyword.
  You can also pass :uuid as a keyword argument e.g. 'afbc0a01-742e-49da-939e-3eaa3cf431b0' for
  'BLACK SEA'. If the keyword is a duplicate, it will substitute the correct one."
  [keyword-map-list keyword]
  (if (contains? duplicate-keywords keyword)
    (first (find-spatial-keywords-in-map keyword-map-list (:uuid (get duplicate-keywords keyword))))
    (first (sort-by count (find-spatial-keywords-in-map keyword-map-list keyword)))))

(defn spatial-keywords->location-keywords
  "Takes a keyword map list and a list of Spatial Keywords and returns a list of location keyword maps
  for that context"
  [keyword-map-list spatial-keywords]
  (map #(find-spatial-keyword keyword-map-list %) spatial-keywords))

(defn translate-spatial-keywords
  "Translates a list of spatial keywords into an array of LocationKeyword type objects"
  [context spatial-keywords]
  (let [spatial-keyword-maps (get-spatial-keywords-maps context)
        location-keyword-maps (spatial-keywords->location-keywords spatial-keyword-maps spatial-keywords)
        umm-location-keyword-maps (seq
                                   (map #(dissoc (set/rename-keys % {:category :Category
                                                             :type :Type
                                                             :subregion-1 :Subregion1
                                                             :subregion-2 :Subregion2
                                                             :subregion-3 :Subregion3}) :uuid nil)
                                        location-keyword-maps))]
    umm-location-keyword-maps))
