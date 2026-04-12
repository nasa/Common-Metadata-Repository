(ns cmr.indexer.data.concepts.collection.project-keyword
  "Contains functions for converting project hierarchies into elastic documents"
  (:require
   [clojure.string :as string]
   [cmr.common-app.services.kms-fetcher :as kf]
   [cmr.common-app.services.kms-lookup :as kms-lookup]))

(def default-project-keyword-values
  "Default values to use for any project fields which are nil."
  (zipmap [:long-name]
          (repeat kf/FIELD_NOT_PRESENT)))

(defn project-short-name->elastic-doc
  "Converts an project short-name into an elastic document with the full nested hierarchy for
  that short-name from the GCMD KMS keywords. If a field is not present in the KMS hierarchy, we
  use a dummy value to indicate the field was not present."
  [context short-name]
  (def c1 context)
  (let [full-project
        (merge default-project-keyword-values
               (kms-lookup/lookup-by-short-name context :projects short-name))
        {:keys [bucket short-name long-name uuid]
         ;; Use the short-name from KMS if present, otherwise use the metadata short-name
         :or {short-name short-name}} full-project]
    {:bucket bucket
     :bucket-lowercase (when bucket (string/lower-case bucket))
     :short-name short-name
     :short-name-lowercase (string/lower-case short-name)
     :long-name long-name
     :long-name-lowercase (string/lower-case long-name)
     :uuid uuid
     :uuid-lowercase (when uuid (string/lower-case uuid))}))
