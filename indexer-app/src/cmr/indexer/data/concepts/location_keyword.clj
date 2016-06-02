(ns cmr.indexer.data.concepts.location-keyword
  "Contains functions for converting location keyword hierarchies into elastic documents"
  (:require [clojure.string :as str]
            [cmr.umm-spec.location-keywords :as lk]
            [cmr.common-app.services.kms-fetcher :as kf]))

(def default-location
  "Default values to use for any platform fields which are nil."
  (zipmap [:category :type :subregion-1 :subregion-2 :subregion-3]
          (repeat kf/FIELD_NOT_PRESENT)))

(defn spatial-keyword->elastic-doc
  "Converts a single spatial keyword string into an elastic document with the full nested hierarchy
  for the best match from the GCMD KMS keywords. If a field is not present in the KMS hierarchy, we
  use a dummy value to indicate the field was not present."
  [gcmd-keywords-map spatial-keyword]
  (let [hierarchical-location (merge default-location
                                     (lk/find-spatial-keyword
                                      (:spatial-keywords gcmd-keywords-map) spatial-keyword))
        {:keys [category type subregion-1 subregion-2 subregion-3 uuid]} hierarchical-location]
    {:category category
     :category.lowercase (str/lower-case category)
     :type type
     :type.lowercase (str/lower-case type)
     :subregion-1 subregion-1
     :subregion-1.lowercase (str/lower-case subregion-1)
     :subregion-2 subregion-2
     :subregion-2.lowercase (str/lower-case subregion-2)
     :subregion-3 subregion-3
     :subregion-3.lowercase (str/lower-case subregion-3)
     :uuid uuid
     :uuid.lowercase (when uuid (str/lower-case uuid))}))
