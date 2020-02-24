(ns cmr.indexer.data.concepts.collection.location-keyword
  "Contains functions for converting location keyword hierarchies into elastic documents"
  (:require
   [clojure.string :as str]
   [cmr.common-app.services.kms-fetcher :as kf]
   [cmr.common-app.services.kms-lookup :as kms-lookup]
   [cmr.common.util :as util]
   [cmr.umm-spec.location-keywords :as lk]))

(def default-location
  "Default values to use for any location fields which are nil."
  (zipmap [:category :type :subregion-1 :subregion-2 :subregion-3]
          (repeat kf/FIELD_NOT_PRESENT)))

(defn location-keyword->elastic-doc
  "Converts a single location-keyword map into an elastic document with the full nested hierarchy
  for the match from the GCMD KMS keywords. Note: :detailed-location is removed because it's not
  defined in KMS and won't be used for the matching. If a field is not present in the KMS hierarchy,
  we use a dummy value to indicate the field was not present, except for uuid which will be nil."
  [kms-index location-keyword]
  (let [location-keyword-kebab-key (util/remove-nil-keys
                                     (util/map-keys->kebab-case location-keyword))
        hierarchical-location (merge default-location
                                     location-keyword-kebab-key
                                     (kms-lookup/lookup-by-umm-c-keyword
                                       kms-index
                                       :spatial-keywords
                                       (dissoc location-keyword-kebab-key :detailed-location)))
        {:keys [category type subregion-1 subregion-2 subregion-3 uuid detailed-location]}
        hierarchical-location]
    {:category category
     :category-lowercase (str/lower-case category)
     :type type
     :type-lowercase (str/lower-case type)
     :subregion-1 subregion-1
     :subregion-1-lowercase (str/lower-case subregion-1)
     :subregion-2 subregion-2
     :subregion-2-lowercase (str/lower-case subregion-2)
     :subregion-3 subregion-3
     :subregion-3-lowercase (str/lower-case subregion-3)
     :uuid uuid
     :uuid-lowercase (when uuid (str/lower-case uuid))
     :detailed-location detailed-location
     :detailed-location-lowercase (util/safe-lowercase detailed-location)}))
