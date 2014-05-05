(ns cmr.search.data.query-to-elastic
  "Defines protocols and functions to map from a query model to elastic search query"
  (:require [clojurewerkz.elastisch.query :as q]
            [clojure.string :as s]
            [cmr.search.models.query :as qm]
            [cmr.search.data.datetime-helper :as h]))

(def field-mappings
  "A map of fields in the query to the field name in elastic. Field names are excluded from this
  map if the query field name matches the field name in elastic search."
  {:collection {:provider :provider-id
                :version :version-id
                :project :project-sn
                :two-d-coordinate-system-name :two-d-coord-name}
   :granule {:provider :provider-id
             :producer-granule-id :producer-gran-id
             :project :project-refs}})

(defn query-field->elastic-field
  "Returns the elastic field name for the equivalent query field name."
  [field concept-type]
  (get-in field-mappings [concept-type field] field))

(defprotocol ConditionToElastic
  "Defines a function to map from a query to elastic search query"
  (condition->elastic
    [condition concept-type]
    "Converts a query model condition into the equivalent elastic search filter"))

(defn query->elastic
  "Converts a query model into an elastic search query"
  [query]
  (let [{:keys [concept-type condition]} query]
    {:filtered {:query (q/match-all)
                :filter (condition->elastic condition concept-type)}}))

(extend-protocol ConditionToElastic
  cmr.search.models.query.ConditionGroup
  (condition->elastic
    [{:keys [operation conditions]} concept-type]
    ;; TODO Performance Improvement: We should order the conditions within and/ors.
    {operation {:filters (map #(condition->elastic % concept-type) conditions)}})

  cmr.search.models.query.StringCondition
  (condition->elastic
    [{:keys [field value case-sensitive? pattern?]} concept-type]
    (let [field (query-field->elastic-field field concept-type)
          field (if case-sensitive? field (str (name field) ".lowercase"))
          value (if case-sensitive? value (s/lower-case value))]
      (if pattern?
        {:query {:wildcard {field value}}}
        {:term {field value}})))

  cmr.search.models.query.BooleanCondition
  (condition->elastic
    [{:keys [field value]} concept-type]
    (let [field (query-field->elastic-field field concept-type)]
      {:term {field value}}))

  cmr.search.models.query.ExistCondition
  (condition->elastic
    [{:keys [field]} _]
    {:exists {:field field}})

  cmr.search.models.query.MissingCondition
  (condition->elastic
    [{:keys [field]} _]
    {:missing {:field field}})

  cmr.search.models.query.DateRangeCondition
  (condition->elastic
    [{:keys [field start-date end-date]} _]
    (let [from-value (if start-date (h/utc-time->elastic-time start-date) h/earliest-echo-start-date)
          value {:from from-value}
          value (if end-date (assoc value :to (h/utc-time->elastic-time end-date)) value)]
      {:range { field value }}))

  cmr.search.models.query.MatchAllCondition
  (condition->elastic
    [_ _]
    {:match_all {}})

  cmr.search.models.query.MatchNoneCondition
  (condition->elastic
    [_ _]
    {:term {:match_none "none"}}))
