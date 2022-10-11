(ns cmr.search.results-handlers.tools-json-results-handler
  "Handles extracting elasticsearch tool results and converting them into a JSON search response."
  (:require
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [cmr.common-app.services.search :as qs]
   [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
   [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
   [cmr.common.util :as util]
   [cmr.search.results-handlers.results-handler-util :as rs-util]))

(defmethod elastic-search-index/concept-type+result-format->fields [:tool :json]
  [_concept-type _query]
  ["concept-id" "revision-id" "deleted" "provider-id" "native-id" "tool-name" "long-name" "associations-gzip-b64"])

(defmethod elastic-results/elastic-result->query-result-item [:tool :json]
  [_context _query elastic-result]
  (let [{{tool-name :tool-name
          long-name :long-name
          deleted :deleted
          provider-id :provider-id
          native-id :native-id
          concept-id :concept-id
          associations-gzip-b64 :associations-gzip-b64} :_source} elastic-result
        revision-id (elastic-results/get-revision-id-from-elastic-result :tool elastic-result)
        associations (some-> associations-gzip-b64
                             util/gzip-base64->string
                             edn/read-string)
        result-item (util/remove-nil-keys
                     {:concept_id concept-id
                      :revision_id revision-id
                      :provider_id provider-id
                      :native_id native-id
                      :name tool-name
                      :long_name long-name
                      :associations (rs-util/build-association-concept-id-list associations :tool)
                      :association-details (rs-util/build-association-details associations :tool)})]
    (if deleted
      (assoc result-item :deleted deleted)
      result-item)))

(defmethod qs/search-results->response [:tool :json]
  [_context _query results]
  (json/generate-string (select-keys results [:hits :took :items])))
