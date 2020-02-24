(ns cmr.indexer.data.concepts.collection.instrument
  "Contains functions for converting instrument hierarchies into elastic documents"
  (:require [clojure.string :as str]
            [cmr.common-app.services.kms-fetcher :as kf]
            [cmr.common-app.services.kms-lookup :as kms-lookup]))

(def default-instrument-values
  "Default values to use for any platform fields which are nil."
  (zipmap [:category :class :type :subtype :long-name]
          (repeat kf/FIELD_NOT_PRESENT)))

(defn instrument-short-name->elastic-doc
  "Converts an instrument short-name into an elastic document with the full nested hierarchy for
  that short-name from the GCMD KMS keywords. If a field is not present in the KMS hierarchy, we
  use a dummy value to indicate the field was not present."
  [kms-index short-name]
  (let [full-instrument
        (merge default-instrument-values
               (kms-lookup/lookup-by-short-name kms-index :instruments short-name))
        {:keys [category type subtype short-name long-name uuid]
         ;; Use the short-name from KMS if present, otherwise use the metadata short-name
         :or {short-name short-name} i-class :class} full-instrument]
    {:category category
     :category-lowercase (str/lower-case category)
     :class i-class
     :class-lowercase (str/lower-case i-class)
     :type type
     :type-lowercase (str/lower-case type)
     :subtype subtype
     :subtype-lowercase (str/lower-case subtype)
     :short-name short-name
     :short-name-lowercase (str/lower-case short-name)
     :long-name long-name
     :long-name-lowercase (str/lower-case long-name)
     :uuid uuid
     :uuid-lowercase (when uuid (str/lower-case uuid))}))
