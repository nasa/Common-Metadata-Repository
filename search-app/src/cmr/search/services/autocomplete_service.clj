(ns cmr.search.services.autocomplete-service
  "Service for autocomplete functionality"
  (:require
    [cheshire.core :as json]
    [cmr.common-app.services.search.params :as common-params]
    [cmr.common-app.services.search.query-execution :as qe]
    [cmr.common-app.services.search.query-model :as qm]))

(defn autocomplete
  "Execute elasticsearch query to get autocomplete suggestions"
  [context term]
  (let [condition (qm/text-condition :value term)
        query     (qm/query
                   {:concept-type  :autocomplete
                    :condition     condition
                    :result-fields [:type :value]})
        results   (qe/execute-query context query)]
    (let [hits  (:hits results)
          items (:items results)]
      {:hits hits :items items})))
