(ns cmr.search.results-handlers.tags-json-results-handler
  "Handles extracting elasticsearch tag results and converting them into a JSON search response."
  (:require [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
            [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
            [cmr.common-app.services.search :as qs]
            [cmr.common.util :as util]
            [cheshire.core :as json]))

(defmethod elastic-search-index/concept-type+result-format->fields [:tag :json]
  [concept-type query]
  ["concept-id" "tag-key" "description" "originator-id"])

(defmethod elastic-results/elastic-result->query-result-item [:tag :json]
  [context query elastic-result]
  (let [{{[tag-key] :tag-key
          [description] :description
          [concept-id] :concept-id
          [originator-id] :originator-id} :fields
         revision-id :_version} elastic-result]
    {:concept-id concept-id
     :revision-id revision-id
     :tag-key tag-key
     :description description
     :originator-id originator-id}))

(defmethod qs/search-results->response [:tag :json]
  [context query results]
  (json/generate-string (select-keys results [:hits :took :items])))