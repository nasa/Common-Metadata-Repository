(ns cmr.search.results-handlers.autocomplete-results-handler
  "Handles the Autocomplete JSON results format and related functions"
  (:require
    [cmr.common-app.services.search.elastic-results-to-query-results :as er]))

(defmethod er/elastic-result->query-result-item [:autocomplete :json]
  [context query elastic-result]
  (let [{type :type
         value :value
         field :fields} (:_source elastic-result)
        score (:_score elastic-result)]
    {:score score
     :type  type
     :value value
     :fields field}))

(defmethod er/elastic-results->query-results [:autocomplete :json]
  [context query elastic-result]
  (let [hits (get-in elastic-result [:hits :total :value])
        took (:took elastic-result)
        elastic-matches (get-in elastic-result [:hits :hits])
        items (mapv #(er/elastic-result->query-result-item context query %) elastic-matches)]
    {:items items
     :hits hits
     :took took}))
