(ns cmr.search.results-handlers.generics-json-results-handler
  "Handles extracting elasticsearch generic concept results and converting them into a JSON search response."
  (:require
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [clojure.string :as string]
   [cmr.common-app.data.metadata-retrieval.collection-metadata-cache :as cmn-coll-metadata-cache]
   [cmr.common-app.services.search :as qs]
   [cmr.common.concepts :as concepts]
   [cmr.common.log :as log :refer [debug]]
   [cmr.common.services.search.results-model :as results]
   [cmr.common.util :as util]
   [cmr.elastic-utils.search.es-index :as elastic-search-index]
   [cmr.elastic-utils.search.es-results-to-query-results :as er-to-qr]
   [cmr.metadata-db.services.concept-service :as metadata-db]
   [cmr.search.results-handlers.results-handler-util :as rs-util]
   [cmr.search.results-handlers.umm-json-results-helper :as results-helper]
   [cmr.umm-spec.umm-spec-core :as umm]))

(defn- fetch-metadata
  "Fetches metadata from Metadata DB for the given concept tuples."
  [context concept-tuples]
  (when (seq concept-tuples)
    (let [mdb-context (cmn-coll-metadata-cache/context->metadata-db-context context)
          ;; Get Concepts from Metadata db
          [t1 concepts] (util/time-execution
                         (doall (metadata-db/get-concepts mdb-context concept-tuples true)))]
      (debug "fetch of " (count concept-tuples) " concepts:" "get-concepts:" t1)
      concepts)))

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
        tuples (mapv (partial results-helper/elastic-result->tuple concept-type) elastic-matches)
        concepts (fetch-metadata context tuples)
        ;; Convert concepts into items with parsed umm.
        items (mapv (fn [elastic-result concept]
                      (if (:deleted concept)
                        {:meta (results-helper/elastic-result->meta concept-type elastic-result)}
                        (let [metadata (:metadata concept)
                              metadata (if (string/includes? (:format concept) "json")
                                         metadata
                                         (let [concept-type (:concept-type concept)]
                                           (if (concepts/is-draft-concept? concept-type)
                                             (let [draft-concept-type (concepts/get-concept-type-of-draft concept-type)
                                                   concept (assoc concept :concept-type draft-concept-type)]
                                               (json/encode (umm/parse-metadata context concept)))
                                             (json/encode (umm/parse-metadata context concept)))))]
                          (results-helper/elastic-result+metadata->umm-json-item
                           concept-type elastic-result metadata))))
                    elastic-matches
                    concepts)]
    (results/map->Results {:hits hits
                           :timed-out timed-out
                           :items items
                           :result-format result-format
                           :scroll-id scroll-id
                           :search-after search-after})))

(doseq [concept-type (concepts/get-generic-concept-types-array)]
  (defmethod elastic-search-index/concept-type+result-format->fields [concept-type :json]
    [concept-type query]
    ["concept-id" "revision-id" "deleted" "provider-id" "native-id" "name" "id" "associations-gzip-b64"]))

(doseq [concept-type (concepts/get-generic-concept-types-array)]
  (defmethod er-to-qr/elastic-result->query-result-item [concept-type :json]
    [context query elastic-result]
    (let [{{name :name
            id :id
            deleted :deleted
            provider-id :provider-id
            native-id :native-id
            concept-id :concept-id
            associations-gzip-b64 :associations-gzip-b64} :_source} elastic-result
          revision-id (er-to-qr/get-revision-id-from-elastic-result concept-type elastic-result)
          associations (some-> associations-gzip-b64
                               util/gzip-base64->string
                               edn/read-string)
          result-item (util/remove-nil-keys
                       {:concept_id concept-id
                        :revision_id revision-id
                        :provider_id provider-id
                        :native_id native-id
                        :name name
                        :id id
                        :associations (rs-util/build-association-concept-id-list associations concept-type)
                        :association_details (rs-util/build-association-details (rs-util/replace-snake-keys associations) concept-type)})]
      (if deleted
        (assoc result-item :deleted deleted)
        result-item))))

(doseq [doseq-concept-type (concepts/get-generic-concept-types-array)]
  (defmethod qs/search-results->response [doseq-concept-type :json]
    [context query results]
    (json/generate-string (select-keys results [:hits :took :items])))

  (defmethod elastic-search-index/concept-type+result-format->fields [doseq-concept-type :umm-json-results]
    [concept-type query]
    results-helper/meta-fields)

  (defmethod results-helper/elastic-result+metadata->umm-json-item doseq-concept-type
    [concept-type elastic-result metadata]
    {:meta (results-helper/elastic-result->meta doseq-concept-type elastic-result)
     :umm (json/decode metadata)})

  (defmethod er-to-qr/elastic-results->query-results [doseq-concept-type :umm-json-results]
    [context query elastic-results]
    (query-elastic-results->query-results context doseq-concept-type query elastic-results))

  (defmethod qs/search-results->response [doseq-concept-type :umm-json-results]
    [context query results]
    (json/generate-string (select-keys results [:hits :took :items]))))
