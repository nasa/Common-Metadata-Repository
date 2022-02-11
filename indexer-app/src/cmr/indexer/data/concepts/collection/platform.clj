(ns cmr.indexer.data.concepts.collection.platform
  "Contains functions for converting platform hierarchies into elastic documents"
  (:require
    [clojure.string :as string]
    [cmr.common-app.services.kms-fetcher :as kf]
    [cmr.common-app.services.kms-lookup :as kms-lookup]
    [cmr.common.util :as util]))

;
;; *****************************************************************************
;; version 2 platforms

(def default-platform-values
  "Default values to use for any platform fields which are nil. Note: short-name
   should not be nil as it is the lookup key."
  (zipmap [:basis :category :sub-category :long-name]
          (repeat kf/FIELD_NOT_PRESENT)))

(defn platform2-nested-fields->elastic-doc
  "Converts a platform short-name into an elastic document with the full nested
   hierarchy for that short-name from the GCMD KMS keywords. If a field is not
   present in the KMS hierarchy, we use a dummy value to indicate the field was
   not present."
  [kms-index short-name]
  (let [full-platform
        (kms-lookup/lookup-by-short-name kms-index :platforms short-name)
        {:keys [basis category sub-category short-name long-name uuid]
         ;; Use the short-name from KMS if present, otherwise use the metadata short-name
         :or {short-name short-name}} full-platform]
    {:basis basis
     :basis-lowercase (util/safe-lowercase basis)
     :category category
     :category-lowercase (util/safe-lowercase category)
     :sub-category sub-category
     :sub-category-lowercase (util/safe-lowercase sub-category)
     :short-name short-name
     :short-name-lowercase (util/safe-lowercase short-name)
     :long-name long-name
     :long-name-lowercase (util/safe-lowercase long-name)
     :uuid uuid
     :uuid-lowercase (util/safe-lowercase uuid)}))

;; *****************************************************************************
;; version 1 platforms

;DEPRECATED
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
    {:category category
     :category-lowercase (string/lower-case category)
     :series-entity sub-category
     :series-entity-lowercase (string/lower-case sub-category)
     :short-name short-name
     :short-name-lowercase (string/lower-case short-name)
     :long-name long-name
     :long-name-lowercase (string/lower-case long-name)
     :uuid uuid
     :uuid-lowercase (util/safe-lowercase uuid)}))
