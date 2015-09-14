(ns cmr.search.results-handlers.tags-json-results-handler
  "Handles extracting elasticsearch tag results and converting them into a JSON search response."
  (:require [cmr.search.data.elastic-results-to-query-results :as elastic-results]
            [cmr.search.data.elastic-search-index :as elastic-search-index]))

(defmethod elastic-search-index/concept-type+result-format->fields [:tag :json]
  [concept-type query]
  ["concept-id" "namespace" "category" "value" "description"])


(defmethod elastic-results/elastic-results->query-results [:tag :json]
  [context query elastic-results]

  ; (elastic-results/elastic-results->query-results context (assoc query :result-format :atom) elastic-results)

  )
