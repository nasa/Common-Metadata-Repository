(ns cmr.search.services.autocomplete-service
  "Service for autocomplete functionality"
  (:require
    [cmr.common-app.services.search.query-execution :as qe]
    [cmr.common-app.services.search.query-model :as qm]
    [cmr.common-app.services.search.group-query-conditions :as gc]))

(defn autocomplete
  "Execute elasticsearch query to get autocomplete suggestions"
  [context term types opts]
  (let [condition (if (empty? types)
                    (qm/text-condition :value term)
                    (gc/and-conds
                     [(gc/or-conds
                       (map (partial qm/text-condition :type) types))
                      (qm/text-condition :value term)]))
        query     (qm/query
                   {:concept-type :autocomplete
                    :page-size (:page-size opts)
                    :offset (:offset opts)
                    :condition condition
                    :result-fields [:type :value]})
        results   (qe/execute-query context query)
        hits      (:hits results)
        items     (:items results)]
    {:hits hits :items items}))
