(ns cmr.search.results-handlers.umm-json-results-helper
  "Helper functions and definitions for handling common umm-json results format."
  (:require
   [clojure.edn :as edn]
   [clojure.string :as string]
   [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
   [cmr.common-app.services.search.results-model :as results]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.search.data.metadata-retrieval.metadata-cache :as metadata-cache]))

(def meta-fields
  "Defines the fields in elastic search we retrieve to populate the meta fields in the response."
  ["concept-id"
   "revision-id"
   "native-id"
   "user-id"
   "provider-id"
   "metadata-format"
   "revision-date"
   "deleted"
   "has-variables"
   "has-formats"
   "has-transforms"
   "associations-gzip-b64"])

(defn elastic-result->meta
  "Takes an elasticsearch result and returns a map of the meta fields for the response."
  [concept-type elastic-result]
  (let [{[concept-id] :concept-id
         [revision-id] :revision-id
         [native-id] :native-id
         [user-id] :user-id
         [provider-id] :provider-id
         [metadata-format] :metadata-format
         [revision-date] :revision-date
         [deleted] :deleted
         [has-variables] :has-variables
         [has-formats] :has-formats
         [has-transforms] :has-transforms
         [associations-gzip-b64] :associations-gzip-b64} (:fields elastic-result)
        revision-date (when revision-date (string/replace (str revision-date) #"\+0000" "Z"))]
    (util/remove-nil-keys
     {:concept-type concept-type
      :concept-id concept-id
      :revision-id revision-id
      :native-id native-id
      :user-id user-id
      :provider-id provider-id
      :format (mt/format->mime-type (keyword metadata-format))
      :revision-date revision-date
      :deleted deleted
      :has-variables has-variables
      :has-formats has-formats
      :has-transforms has-transforms
      :associations (some-> associations-gzip-b64
                            util/gzip-base64->string
                            edn/read-string)})))

(defn elastic-result->tuple
  "Returns a tuple of concept id and revision id from the elastic result of the given concept type."
  [concept-type elastic-result]
  [(get-in elastic-result [:fields :concept-id 0])
   (elastic-results/get-revision-id-from-elastic-result concept-type elastic-result)])

(defmulti elastic-result+metadata->umm-json-item
  "Returns the UMM JSON item for the given concept type, elastic result and metadata."
  (fn [concept-type elastic-result metadata]
    concept-type))

(defn query-elastic-results->query-results
  "Returns the query results for the given concept type, query and elastic results."
  [context concept-type query elastic-results]
  (let [{:keys [result-format]} query
        hits (get-in elastic-results [:hits :total])
        elastic-matches (get-in elastic-results [:hits :hits])
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
    (results/map->Results {:hits hits :items items :result-format result-format})))
