(ns cmr.indexer.data.concepts.collection.project-keyword
  "Contains functions for converting project hierarchies into elastic documents"
  (:require
   [cmr.common-app.services.kms-lookup :as kms-lookup]))

(defn project-short-name->elastic-doc
  "Converts a project short-name into an elastic document with the uuid
  for that short-name from the GCMD KMS keywords."
  [context short-name]
  (let [{:keys [uuid]} (kms-lookup/lookup-by-short-name context :projects short-name)]
    (when uuid
      {:uuid uuid})))
