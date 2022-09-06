(ns cmr.search.results-handlers.generics-json-results-handler
  "Handles extracting elasticsearch generic concept results and converting them into a JSON search response."
  (:require
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [cmr.common.concepts :as concepts]
   [cmr.common.util :as util]
   [cmr.common-app.services.search :as qs]
   [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
   [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]))

(doseq [concept-type (concepts/get-generic-concept-types-array)]
  (defmethod elastic-search-index/concept-type+result-format->fields [concept-type :json]
    [concept-type query]
    ["concept-id" "revision-id" "deleted" "provider-id" "native-id" "name" "id" "associations-gzip-b64"]))
  
(doseq [concept-type (concepts/get-generic-concept-types-array)]
  (defmethod elastic-results/elastic-result->query-result-item [concept-type :json]
    [context query elastic-result]
    (let [{{name :name
            id :id
            deleted :deleted
            provider-id :provider-id
            native-id :native-id
            concept-id :concept-id
            associations-gzip-b64 :associations-gzip-b64} :_source} elastic-result
          revision-id (elastic-results/get-revision-id-from-elastic-result concept-type elastic-result)
          result-item (util/remove-nil-keys
                       {:concept_id concept-id
                        :revision_id revision-id
                        :provider_id provider-id
                        :native_id native-id
                        :name name
                        :id id
                        :generic-associations (some-> associations-gzip-b64
                                                      util/gzip-base64->string
                                                      edn/read-string)})]
      (if deleted
        (assoc result-item :deleted deleted)
        result-item))))

(doseq [concept-type (concepts/get-generic-concept-types-array)]
  (defmethod qs/search-results->response [concept-type :json]
    [context query results]
    (json/generate-string (select-keys results [:hits :took :items]))))
