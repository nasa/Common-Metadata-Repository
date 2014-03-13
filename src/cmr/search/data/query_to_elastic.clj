(ns cmr.search.data.query-to-elastic
  "Defines protocols and functions to map from a query model to elastic search query"
  (:require [clojurewerkz.elastisch.query :as q]
            [cmr.search.models.query :as qm]))

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
    (let [field (if case-sensitive? field (str field ".lowercase"))]
      (if pattern?
        ;; TODO when it's a pattern we have to convert the % and _ to equivalent in elastic
        {:query {:wildcard {field value}}}
        {:term {field value}})))

  cmr.search.models.query.MatchAllCondition
  (condition->elastic
    [_]
    {:match_all {}}))