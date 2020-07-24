(ns cmr.search.services.autocomplete-service
  "Service for autocomplete functionality"
  (:require
    [cmr.common-app.services.search.query-execution :as qe]
    [cmr.common-app.services.search.query-model :as qm]
    [cmr.common-app.services.search.group-query-conditions :as gc]))

(def token-condition
  (qm/boolean-condition :contains-public-collections true))

(defn- empty-token-with-type
  [term types]
  (let [types-condition (gc/or-conds (map (partial qm/text-condition :type) types))]
    (qm/match-filter :value term (gc/and-conds [token-condition types-condition]))))

(defn- empty-token-without-type
  [term]
  (qm/match-filter :value term token-condition))

(defn- non-empty-token-with-type
  [term types]
  (let [type-conditions (gc/or-conds (map (partial qm/text-condition :type) types))]
    (qm/match-filter :value term type-conditions)))

(defn- non-empty-token-without-type
  [term]
  (qm/match :value term))

(defn- autocomplete-condition
  "Take in terms, types, tokens, and return query condition based on which are present."
  [term types token]
  (cond
    (and (empty? token)(empty? types)) (empty-token-without-type term)
    (and (empty? token) (seq types)) (empty-token-with-type term types)
    (and (seq token)) (seq types)) (non-empty-token-with-type term types)
    (and (seq token)) (empty? types) (non-empty-token-without-type term))

(defn build-autocomplete-condition
  [term types]
  (let [root (gc/or-conds [(qm/match :value term)
                           (qm/multi-match :phrase_prefix
                                           ["value" "value._2gram" "value._3gram"]
                                           term)])]
    (if (empty? types)
      root
      (gc/and-conds [root
                     (gc/or-conds (map #(qm/text-condition :type %) types))]))))

(defn autocomplete
  "Execute elasticsearch query to get autocomplete suggestions"
  [context term types opts token]
  (let [condition (autocomplete-condition term types token)
        query (qm/query {:concept-type :autocomplete
                         :page-size (:page-size opts)
                         :offset (:offset opts)
                         :condition condition
                         :result-fields [:type :value :fields]})]
    (qe/execute-query context query)))
