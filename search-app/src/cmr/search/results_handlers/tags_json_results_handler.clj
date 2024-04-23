(ns cmr.search.results-handlers.tags-json-results-handler
  "Handles extracting elasticsearch tag results and converting them into a JSON search response."
  (:require
   [cheshire.core :as json]
   [cmr.common-app.services.search :as qs]
   [cmr.elastic-utils.search.es-index :as elastic-search-index]
   [cmr.elastic-utils.search.es-results-to-query-results :as elastic-results]))

(defmethod elastic-search-index/concept-type+result-format->fields [:tag :json]
  [_concept-type _query]
  ["concept-id" "tag-key" "description" "originator-id"])

(defmethod elastic-results/elastic-result->query-result-item [:tag :json]
  [_context _query elastic-result]
  (let [{{tag-key :tag-key
          description :description
          concept-id :concept-id
          originator-id :originator-id} :_source} elastic-result
        revision-id (elastic-results/get-revision-id-from-elastic-result :tag elastic-result)]
    {:concept-id concept-id
     :revision-id revision-id
     :tag-key tag-key
     :description description
     :originator-id originator-id}))

(defmethod qs/search-results->response [:tag :json]
  [_context _query results]
  (json/generate-string (select-keys results [:hits :took :items])))
