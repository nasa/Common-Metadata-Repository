(ns cmr.search.results-handlers.autocomplete-results-handler
  "Handles the Autocomplete JSON results format and related functions"
  (:require
    [cmr.common-app.services.search.elastic-results-to-query-results
     :as
     elastic-results]))

(defmethod elastic-results/elastic-result->query-result-item [:autocomplete :json]
  [context query elastic-result]
  (let [{[type]  :type
         [value] :value}(:fields elastic-result)
        score (:_score elastic-result)]
    (println elastic-result)
    {:score score
     :type  type
     :value value}))
