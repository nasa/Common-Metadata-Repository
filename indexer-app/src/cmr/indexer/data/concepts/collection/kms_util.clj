(ns cmr.indexer.data.concepts.collection.kms-util
  "Contains functions for converting KMS hierarchies into elastic documents"
  (:require
   [cmr.common-app.services.kms-lookup :as kms-lookup]))

(defn project-short-name->elastic-doc
  "Converts a project short-name into an elastic document with the uuid
  for that short-name from the GCMD KMS keywords."
  [context short-name]
  (let [uuid (kms-lookup/lookup-project-by-short-name context short-name)]
    (tap> uuid)
    (when uuid
      {:uuid uuid})))

(defn processing-level-id->elastic-doc
  "Converts a processing level id into an elastic document with the uuid
  for that processing level id from the GCMD KMS keywords."
  [context processing-level-id]
  (let [uuid (kms-lookup/lookup-processing-level-by-id context processing-level-id)]
    (when uuid
      {:uuid uuid})))
