(ns cmr.indexer.data.concepts.collection.platform
  "Contains functions for converting platform hierarchies into elastic documents"
  (:require [clojure.string :as str]
            [cmr.common-app.services.kms-fetcher :as kf]
            [cmr.common-app.services.kms-lookup :as kms-lookup]))


(def default-platform-values
  "Default values to use for any platform fields which are nil."
  (zipmap [:category :series-entity :long-name]
          (repeat kf/FIELD_NOT_PRESENT)))

(defn platform-short-name->elastic-doc
  "Converts a platform short-name into an elastic document with the full nested hierarchy for that
  short-name from the GCMD KMS keywords. If a field is not present in the KMS hierarchy, we use a
  dummy value to indicate the field was not present."
  [kms-index short-name]
  (let [full-platform
        (merge default-platform-values
               (kms-lookup/lookup-by-short-name kms-index :platforms short-name))
        {:keys [category series-entity short-name long-name uuid]
         ;; Use the short-name from KMS if present, otherwise use the metadata short-name
         :or {short-name short-name}} full-platform]
    {:category category
     :category.lowercase (str/lower-case category)
     :series-entity series-entity
     :series-entity.lowercase (str/lower-case series-entity)
     :short-name short-name
     :short-name.lowercase (str/lower-case short-name)
     :long-name long-name
     :long-name.lowercase (str/lower-case long-name)
     :uuid uuid
     :uuid.lowercase (when uuid (str/lower-case uuid))}))
