(ns cmr.search.services.autocomplete-service
  "Service for autocomplete functionality"
  (:require
    [cmr.common-app.services.search.query-execution :as qe]
    [cmr.common-app.services.search.query-model :as qm]
    [cmr.common-app.services.search.group-query-conditions :as gc]))

(defn- build-autocomplete-condition
  [term types]
  (let [root (qm/multi-match ["value" "value._2gram" "value._3gram"]
                             term
                             {:type "phrase_prefix"})]
    (if (empty? types)
      root
      (gc/and-conds [root
                     (gc/or-conds (map #(qm/text-condition :type %)
                                       types))]))))

(defn autocomplete
  "Execute elasticsearch query to get autocomplete suggestions"
  [context term types opts]
  (let [condition (build-autocomplete-condition term types)
        query (qm/query {:concept-type :autocomplete
                         :page-size (:page-size opts)
                         :offset (:offset opts)
                         :condition condition
                         :result-fields [:type :value :fields]})]
    (qe/execute-query context query)))
