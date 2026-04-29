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
  (let [uuid (:uuid (kms-lookup/lookup-by-umm-c-keyword context :temporal-keywords {:temporal-resolution-range temporal-keyword}))]
    (when uuid
      {:uuid uuid})))

(defn measurement-name->elastic-doc
  "Converts a measurement name into an elastic document with the uuid
  for that measurement name from the GCMD KMS keywords."
  [context measurement-name]
  (let [uuid (:uuid (kms-lookup/lookup-by-umm-c-keyword context :measurement-name measurement-name))]
    (when uuid
      {:uuid uuid})))

(defn concept->elastic-doc
  "Converts a concept into an elastic document with the uuid
  for that concept from the GCMD KMS keywords."
  [context concept]
  (let [uuid (:uuid (kms-lookup/lookup-by-umm-c-keyword context :concepts concept))]
    (when uuid
      {:uuid uuid})))

(defn iso-topic-category->elastic-doc
  "Converts an iso topic category into an elastic document with the uuid
  for that iso topic category from the GCMD KMS keywords."
  [context iso-topic-category]
  (let [uuid (:uuid (kms-lookup/lookup-by-umm-c-keyword context :iso-topic-categories {:iso-topic-category iso-topic-category}))]
    (when uuid
      {:uuid uuid})))

(defn related-url->elastic-doc
  "Converts a related url into an elastic document with the uuid
  for that related url from the GCMD KMS keywords."
  [context related-url]
  (let [uuid (:uuid (kms-lookup/lookup-by-umm-c-keyword context :related-urls related-url))]
    (when uuid
      {:uuid uuid})))

(defn granule-data-format->elastic-doc
  "Converts a granule data format into an elastic document with the uuid
  for that granule data format from the GCMD KMS keywords."
  [context granule-data-format]
  (def c1 context)
  (tap> "foobar")
  (let [uuid (:uuid (kms-lookup/lookup-by-umm-c-keyword context :granule-data-format {:short-name granule-data-format}))]
    ;; (tap> uuid)
    (when uuid
      {:uuid uuid})))

(defn mime-type->elastic-doc
  "Converts a mime type into an elastic document with the uuid
  for that mime type from the GCMD KMS keywords."
  [context mime-type]
  (let [uuid (:uuid (kms-lookup/lookup-by-umm-c-keyword context :mime-type {:mime-type mime-type}))]
    (when uuid
      {:uuid uuid})))

(comment
  (println c1)
  ;; (kms-lookup/lookup-by-umm-c-keyword c1 :granule-data-format {:short-name granule-data-format})
  :rcf)

