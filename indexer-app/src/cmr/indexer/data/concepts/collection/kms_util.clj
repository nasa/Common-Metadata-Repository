(ns cmr.indexer.data.concepts.collection.kms-util
  "Contains functions for converting KMS hierarchies into elastic documents"
  (:require
   [cmr.common-app.services.kms-lookup :as kms-lookup]))

(defn project-short-name->elastic-doc
  "Converts a project short-name into an elastic document with the uuid
  for that short-name from the GCMD KMS keywords."
  [context short-name]
  (let [uuid (kms-lookup/lookup-project-by-short-name context short-name)]
    (when uuid
      {:uuid uuid})))

(defn processing-level-id->elastic-doc
  "Converts a processing level id into an elastic document with the uuid
  for that processing level id from the GCMD KMS keywords."
  [context processing-level-id]
  (let [uuid (kms-lookup/lookup-processing-level-by-id context processing-level-id)]
    (when uuid
      {:uuid uuid})))

(defn temporal-keyword->elastic-doc
  "Converts a temporal keyword into an elastic document with the uuid
  for that temporal keyword from the GCMD KMS keywords."
  [context temporal-keyword]
  (let [uuid (kms-lookup/lookup-temporal-keyword-by-name context temporal-keyword)]
    (when uuid
      {:uuid uuid})))

(defn concept->elastic-doc
  "Converts a concept into an elastic document with the uuid
  for that concept from the GCMD KMS keywords."
  [context concept]
  (let [short-name (if (string? concept)
                     concept
                     (or (:short-name concept)
                         (:ShortName concept)))
        uuid (kms-lookup/lookup-concept-by-short-name context short-name)]
    (when uuid
      {:uuid uuid})))

(defn iso-topic-category->elastic-doc
  "Converts an iso topic category into an elastic document with the uuid
  for that iso topic category from the GCMD KMS keywords."
  [context iso-topic-category]
  (let [uuid (kms-lookup/lookup-iso-topic-category-by-name context iso-topic-category)]
    (when uuid
      {:uuid uuid})))

(defn related-url->elastic-doc
  "Converts a related url into an elastic document with the uuid
  for that related url from the GCMD KMS keywords."
  [context related-url]
  (let [uuid (kms-lookup/lookup-related-url-by-map context related-url)]
    (when uuid
      {:uuid uuid})))

(defn granule-data-format->elastic-doc
  "Converts a granule data format into an elastic document with the uuid
  for that granule data format from the GCMD KMS keywords."
  [context granule-data-format]
  (let [uuid (kms-lookup/lookup-granule-data-format-by-short-name
              context
              (if (string? granule-data-format)
                granule-data-format
                (or (:short-name granule-data-format)
                    (:format granule-data-format))))]
    (when uuid
      {:uuid uuid})))

(defn mime-type->elastic-doc
  "Converts a mime type into an elastic document with the uuid
  for that mime type from the GCMD KMS keywords."
  [context mime-type]
  (let [uuid (kms-lookup/lookup-mime-type-by-name context mime-type)]
    (when uuid
      {:uuid uuid})))

(defn science-keyword->elastic-doc
  "Converts a science keyword into an elastic document with the uuid
  for that science keyword from the GCMD KMS keywords."
  [context science-keyword]
  (let [uuid (kms-lookup/lookup-science-keyword-by-map context science-keyword)]
    (when uuid
      {:uuid uuid})))

(defn platform->elastic-doc
  "Converts a platform into an elastic document with the uuid
  for that platform from the GCMD KMS keywords."
  [context platform]
  (let [uuid (kms-lookup/lookup-platform-by-short-name context platform)]
    (when uuid
      {:uuid uuid})))

(defn instrument->elastic-doc
  "Converts an instrument into an elastic document with the uuid
  for that instrument from the GCMD KMS keywords."
  [context instrument]
  (let [uuid (kms-lookup/lookup-instrument-by-short-name context instrument)]
    (when uuid
      {:uuid uuid})))

