(ns cmr.search.results-handlers.services-json-results-handler
  "Handles extracting elasticsearch service results and converting them into a JSON search response."
  (:require
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [cmr.common-app.services.search :as qs]
   [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
   [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
   [cmr.common.util :as util]
   [cmr.search.results-handlers.results-handler-util :as rs-util]))

(defmethod elastic-search-index/concept-type+result-format->fields [:service :json]
  [_concept-type _query]
  ["concept-id" "revision-id" "deleted" "provider-id" "native-id" "service-name" "long-name" "associations-gzip-b64"])

(defmethod elastic-results/elastic-result->query-result-item [:service :json]
  [_context _query elastic-result]
  (let [{{service-name :service-name
          long-name :long-name
          deleted :deleted
          provider-id :provider-id
          native-id :native-id
          concept-id :concept-id
          associations-gzip-b64 :associations-gzip-b64} :_source} elastic-result
        revision-id (elastic-results/get-revision-id-from-elastic-result :service elastic-result)
        associations (some-> associations-gzip-b64
                             util/gzip-base64->string
                             edn/read-string)
        result-item (util/remove-nil-keys
                     {:concept_id concept-id
                      :revision_id revision-id
                      :provider_id provider-id
                      :native_id native-id
                      :name service-name
                      :long_name long-name
                      :associations (rs-util/build-association-concept-id-list associations :service)
                      :association-details (rs-util/build-association-details associations :service)})]
    (if deleted
      (assoc result-item :deleted deleted)
      result-item)))

(defmethod qs/search-results->response [:service :json]
  [_context _query results]
  (json/generate-string (select-keys results [:hits :took :items])))
