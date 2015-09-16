(ns cmr.search.data.elastic-results-to-query-results
  "Contains functions to convert elasticsearch results to query results."
  (:require [clojure.string :as s]
            [cmr.search.models.results :as results]
            [cmr.search.services.url-helper :as url]))

(defmulti elastic-result->query-result-item
  "Converts the Elasticsearch result into the result expected from execute-query for the given format."
  (fn [context query elastic-result]
    [(:concept-type query) (:result-format query)]))

(defmulti elastic-results->query-results
  "Converts elastic search results to query results"
  (fn [context query elastic-results]
    [(:concept-type query) (:result-format query)]))

(defmethod elastic-results->query-results :default
  [context query elastic-results]
  (let [hits (get-in elastic-results [:hits :total])
        elastic-matches (get-in elastic-results [:hits :hits])
        items (mapv (partial elastic-result->query-result-item context query) elastic-matches)]
    (results/map->Results {:hits hits :items items :result-format (:result-format query)})))


