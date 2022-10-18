(ns cmr.search.results-handlers.generics-json-results-handler
  "Handles extracting elasticsearch generic concept results and converting them into a JSON search response."
  (:require
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [cmr.common.concepts :as concepts]
   [cmr.common.log :as log :refer [debug]]
   [cmr.common.util :as util]
   [cmr.common-app.services.search :as qs]
   [cmr.common-app.services.search.elastic-results-to-query-results :as er-to-qr]
   [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
   [cmr.common-app.services.search.results-model :as results]
   [cmr.metadata-db.services.concept-service :as metadata-db]
   [cmr.search.data.metadata-retrieval.metadata-cache :as metadata-cache]
   [cmr.search.results-handlers.results-handler-util :as rs-util]
   [cmr.search.results-handlers.umm-json-results-helper :as results-helper]))

(defn- fetch-metadata
  "Fetches metadata from Metadata DB for the given concept tuples."
  [context concept-tuples]
  (when (seq concept-tuples)
    (let [mdb-context (metadata-cache/context->metadata-db-context context)
          ;; Get Concepts from Metadata db
          [t1 concepts] (util/time-execution
                         (doall (metadata-db/get-concepts mdb-context concept-tuples false)))]
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
                        (results-helper/elastic-result+metadata->umm-json-item
                         concept-type elastic-result (:metadata concept))))
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
                        :association-details (rs-util/build-association-details associations concept-type)})]
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
