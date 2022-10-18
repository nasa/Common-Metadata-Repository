(ns cmr.search.results-handlers.umm-json-results-helper
  "Helper functions and definitions for handling common umm-json results format."
  (:require
   [clojure.edn :as edn]
   [clojure.string :as string]
   [cmr.common-app.services.search.elastic-results-to-query-results :as er-to-qr]
   [cmr.common-app.services.search.results-model :as results]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.search.data.metadata-retrieval.metadata-cache :as metadata-cache]
   [cmr.search.results-handlers.results-handler-util :as rs-util]))

(def meta-fields
  "Defines the fields in elastic search we retrieve to populate the meta fields in the response."
  ["concept-id"
   "revision-id"
   "native-id"
   "user-id"
   "provider-id"
   "metadata-format"
   "creation-date"
   "revision-date"
   "deleted"
   "has-variables"
   "has-formats"
   "has-transforms"
   "has-spatial-subsetting"
   "has-temporal-subsetting"
   "associations-gzip-b64"
   "s3-bucket-and-object-prefix-names"])

(defn elastic-result->meta
  "Takes an elasticsearch result and returns a map of the meta fields for the response."
  [concept-type elastic-result]
  (let [{:keys [concept-id revision-id native-id user-id provider-id metadata-format
                creation-date revision-date deleted has-variables has-formats has-transforms
                has-spatial-subsetting has-temporal-subsetting
                associations-gzip-b64 s3-bucket-and-object-prefix-names]} (:_source elastic-result)
        creation-date (when creation-date (string/replace (str creation-date) #"\+0000" "Z"))
        revision-date (when revision-date (string/replace (str revision-date) #"\+0000" "Z"))
        associations (some-> associations-gzip-b64
                             util/gzip-base64->string
                             edn/read-string)]
    (util/remove-nil-keys
     {:concept-type concept-type
      :concept-id concept-id
      :revision-id revision-id
      :native-id native-id
      :user-id user-id
      :provider-id provider-id
      :format (mt/format->mime-type (keyword metadata-format))
      :creation-date creation-date
      :revision-date revision-date
      :deleted deleted
      :has-variables has-variables
      :has-formats has-formats
      :has-transforms has-transforms
      :has-spatial-subsetting has-spatial-subsetting
      :has-temporal-subsetting has-temporal-subsetting
      :s3-links s3-bucket-and-object-prefix-names
      :associations (rs-util/build-association-concept-id-list associations concept-type)
      :association-details (rs-util/build-association-details associations concept-type)})))

(defn elastic-result->tuple
  "Returns a tuple of concept id and revision id from the elastic result of the given concept type."
  [concept-type elastic-result]
  [(get-in elastic-result [:_source :concept-id])
   (er-to-qr/get-revision-id-from-elastic-result concept-type elastic-result)])

(defmulti elastic-result+metadata->umm-json-item
  "Returns the UMM JSON item for the given concept type, elastic result and metadata."
  (fn [concept-type elastic-result metadata]
    concept-type))

(defn query-elastic-results->query-results
  "Returns the query results for the given concept type, query and elastic results."
  [context concept-type query elastic-results]
  (let [{:keys [result-format]} query
        hits (er-to-qr/get-hits elastic-results)
        timed-out (er-to-qr/get-timedout elastic-results)
        scroll-id (er-to-qr/get-scroll-id elastic-results)
        search-after (er-to-qr/get-search-after elastic-results)
        elastic-matches (er-to-qr/get-elastic-matches elastic-results)
        ;; Get concept metadata in specified UMM format and version
        tuples (mapv (partial elastic-result->tuple concept-type) elastic-matches)
        concepts (metadata-cache/get-formatted-concept-revisions
                  context concept-type tuples (assoc result-format :format :umm-json))
        ;; Convert concepts into items with parsed umm.
        items (mapv (fn [elastic-result concept]
                      (if (:deleted concept)
                        {:meta (elastic-result->meta concept-type elastic-result)}
                        (elastic-result+metadata->umm-json-item
                         concept-type elastic-result (:metadata concept))))
                    elastic-matches
                    concepts)]
    (results/map->Results {:hits hits
                           :timed-out timed-out
                           :items items
                           :result-format result-format
                           :scroll-id scroll-id
                           :search-after search-after})))
