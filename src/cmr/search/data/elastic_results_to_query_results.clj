(ns cmr.search.data.elastic-results-to-query-results
  "Contains function to covert elasticsearch results to query reference results."
  (:require [clojure.string :as s]
            [cmr.search.models.results :as results]
            [cmr.search.services.url-helper :as url]))

;; TODO get rid of this namespace. Move multimethod definition into elastic-search-index or possibly query execution

(defmulti elastic-result->query-result-item
  "Converts the Elasticsearch result into the result expected from execute-query for the given format."
  (fn [context concept-type result-format elastic-result]
    result-format))


(defmulti elastic-results->query-results
  "TODO document this"
  (fn [context concept-type elastic-results result-format]
    result-format))

(defmethod elastic-results->query-results :default
  [context concept-type elastic-results result-format]
  (let [hits (get-in elastic-results [:hits :total])
        elastic-matches (get-in elastic-results [:hits :hits])
        refs (map (partial elastic-result->query-result-item context concept-type result-format)
                  elastic-matches)]
    (results/map->Results {:hits hits :references refs})))

