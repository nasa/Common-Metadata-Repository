(ns cmr.indexer.data.concepts.collection.platform
  "Contains functions for converting platform hierarchies into elastic documents"
  (:require
    [clojure.string :as str]
    [cmr.common-app.services.kms-fetcher :as kf]
    [cmr.common-app.services.kms-lookup :as kms-lookup]
    [cmr.common.util :as util]))


(def default-platform-values
  "Default values to use for any platform fields which are nil. Note: short-name
   should not be nil as it is the lookup key."
  (zipmap [:basis :category :sub-category :long-name]
          (repeat kf/FIELD_NOT_PRESENT)))

(defn platform-short-name->elastic-doc
  "Converts a platform short-name into an elastic document with the full nested
   hierarchy for that short-name from the GCMD KMS keywords. If a field is not
   present in the KMS hierarchy, we use a dummy value to indicate the field was
   not present."
  [kms-index short-name]
  (let [full-platform
        (merge default-platform-values
               (kms-lookup/lookup-by-short-name kms-index :platforms short-name))
        {:keys [basis category sub-category short-name long-name uuid]
         ;; Use the short-name from KMS if present, otherwise use the metadata short-name
         :or {short-name short-name}} full-platform]
    {:basis basis
     :basis-lowercase (str/lower-case basis)
     :category category
     :category-lowercase (str/lower-case category)
     :sub-category sub-category ;; formally series-entity
     :sub-category-lowercase (str/lower-case sub-category)
     :short-name short-name
     :short-name-lowercase (str/lower-case short-name)
     :long-name long-name
     :long-name-lowercase (str/lower-case long-name)
     :uuid uuid
     :uuid-lowercase (util/safe-lowercase uuid)}))
