(ns cmr.search.results-handlers.tags-json-results-handler
  "Handles extracting elasticsearch tag results and converting them into a JSON search response."
  (:require [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
            [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
            [cmr.search.services.query-service :as qs]
            [cmr.common.util :as util]
            [cheshire.core :as json]))

(defmethod elastic-search-index/concept-type+result-format->fields [:tag :json]
  [concept-type query]
  ["concept-id" "namespace" "category" "value" "description" "originator-id.lowercase"])

(defmethod elastic-results/elastic-result->query-result-item [:tag :json]
  [context query elastic-result]
  (let [{{[tag-namespace] :namespace
          [description] :description
          [category] :category
          [value] :value
          [concept-id] :concept-id
          [originator-id] :originator-id.lowercase} :fields
         revision-id :_version} elastic-result]
    {:concept-id concept-id
     :revision-id revision-id
     :namespace tag-namespace
     :value value
     :category category
     :description description
     :originator-id originator-id}))

(defmethod qs/search-results->response [:tag :json]
  [context query results]
  (json/generate-string (select-keys results [:hits :took :items])))