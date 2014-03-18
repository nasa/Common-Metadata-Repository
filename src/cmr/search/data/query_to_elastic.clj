(ns cmr.search.data.query-to-elastic
  "Defines protocols and functions to map from a query model to elastic search query"
  (:require [clojurewerkz.elastisch.query :as q]
            [clojure.string :as s]
            [cmr.search.models.query :as qm]))

(def field-mappings
  "A map of fields in the query to the field name in elastic. Field names are excluded from this
  map if the query field name matches the field name in elastic search."
  {:entry_title :dataset_id
   :provider :provider_id})

(defn query-field->elastic-field
  "Returns the elastic field name for the equivalent query field name."
  [field]
  (get field-mappings field field))


(defprotocol ConditionToElastic
  "Defines a function to map from a query to elastic search query"
  (condition->elastic
    [c]
    "Converts a query model condition into the equivalent elastic search filter"))

(defn query->elastic
  "Converts a query model into an elastic search query"
  [query]
  {:filtered {:query (q/match-all)
              :filter (condition->elastic (:condition query))}})

(extend-protocol ConditionToElastic
  cmr.search.models.query.ConditionGroup
  (condition->elastic
    [{:keys [operation conditions]}]
    ;; TODO Performance Improvement: We should order the conditions within and/ors.
    {operation {:filters (map condition->elastic conditions)}})

  cmr.search.models.query.StringCondition
  (condition->elastic
    [{:keys [field value case-sensitive? pattern?]}]
    (let [field (query-field->elastic-field field)
          field (if case-sensitive? field (str (name field) ".lowercase"))
          value (if case-sensitive? value (s/lower-case value))]
      (if pattern?
        {:query {:wildcard {field value}}}
        {:term {field value}})))

  cmr.search.models.query.MatchAllCondition
  (condition->elastic
    [_]
    {:match_all {}}))