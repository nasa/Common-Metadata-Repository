(ns cmr.search.results-handlers.autocomplete-results-handler
  "Handles the Autocomplete JSON results format and related functions"
  (:require
    [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]))

(defmethod elastic-results/elastic-result->query-result-item [:autocomplete :json]
  [context query elastic-result]
  (let [{[type] :type
         [value] :value
         [field] :value} (:fields elastic-result)
        score (:_score elastic-result)]
    {:score score
     :type  type
     :value value
     :field field}))

(defmethod elastic-results/elastic-results->query-results [:autocomplete :json]
  [context query elastic-results]
  (let [hits (get-in elastic-results [:hits :total])
        took (:took elastic-results)
        elastic-matches (get-in elastic-results [:hits :hits])
        items (mapv #(elastic-results/elastic-result->query-result-item context query %) elastic-matches)]
    {:items items
     :hits hits
     :took took}))
