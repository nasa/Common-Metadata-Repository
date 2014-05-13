(ns cmr.search.data.query-to-elastic
  "Defines protocols and functions to map from a query model to elastic search query"
  (:require [clojurewerkz.elastisch.query :as q]
            [clojure.string :as s]
            [cmr.search.models.query :as qm]
            [cmr.search.data.datetime-helper :as h]
            [cmr.common.services.errors :as errors]
            [cmr.common.concepts :as cc]
            [cmr.search.data.messages :as m]))

(def field-mappings
  "A map of fields in the query to the field name in elastic. Field names are excluded from this
  map if the query field name matches the field name in elastic search."
  {:collection {:provider :provider-id
                :version :version-id
                :project :project-sn
                :updated-since :revision-date
                :two-d-coordinate-system-name :two-d-coord-name
                :platform :platform-sn
                :instrument :instrument-sn
                :sensor :sensor-sn}

   :granule {:provider :provider-id
             :producer-granule-id :producer-gran-id
             :updated-since :revision-date
             :platform :platform-sn
             :instrument :instrument-sn
             :sensor :sensor-sn
             :project :project-refs}})

(def granule-parent-es-field
  "The granule elastic field that contains its parent collection CMR concept id."
  :collection-concept-id)

(defn query-field->elastic-field
  "Returns the elastic field name for the equivalent query field name."
  ([field concept-type]
   (get-in field-mappings [concept-type field] field))
  ([field concept-type value]
   (if (and (= :granule concept-type)
            (= :concept-id field)
            (= :collection (-> value cc/parse-concept-id  :concept-type)))
     (query-field->elastic-field granule-parent-es-field concept-type)
     (query-field->elastic-field field concept-type))))

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

(def sort-key-field->elastic-field
  "Submaps by concept type of the sort key fields given by the user to the exact elastic sort field to use.
  If a sort key is not in this map it means that it can be used directly with elastic."
  {:collection {:entry-title :entry-title.lowercase
                :provider :provider-id.lowercase
                :platform :platform-sn.lowercase
                :instrument :instrument-sn.lowercase
                :sensor :sensor-sn.lowercase}
   :granule {:provider :provider-id.lowercase
             :entry-title :entry-title.lowercase
             :short-name :short-name.lowercase
             :version :version-id.lowercase
             :granule-ur :granule-ur.lowercase
             :producer-granule-id :producer-gran-id.lowercase
             :readable-granule-name :readable-granule-name-sort
             :data-size :size
             :platform :platform-sn.lowercase
             :instrument :instrument-sn.lowercase
             :sensor :sensor-sn.lowercase
             :project :project-refs.lowercase}})

(defn query->sort-params
  "Converts a query into the elastic parameters for sorting results"
  [query]
  (let [{:keys [concept-type sort-keys]} query]
    (map (fn [{:keys [order field]}]
           {(get-in sort-key-field->elastic-field [concept-type field] (name field))
            {:order order}})
         sort-keys)))

(extend-protocol ConditionToElastic
  cmr.search.models.query.ConditionGroup
  (condition->elastic
    [{:keys [operation conditions]} concept-type]
    ;; TODO Performance Improvement: We should order the conditions within and/ors.
    {operation {:filters (map #(condition->elastic % concept-type) conditions)}})

  cmr.search.models.query.NestedCondition
  (condition->elastic
    [{:keys [path condition]} concept-type]
    {:nested {:path path
              :filter (condition->elastic condition concept-type)}})

  cmr.search.models.query.StringCondition
  (condition->elastic
    [{:keys [field value case-sensitive? pattern?]} concept-type]
    (let [field (query-field->elastic-field field concept-type value)
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

  cmr.search.models.query.NegatedCondition
  (condition->elastic
    [{:keys [condition]} concept-type]
    (hash-map :not (condition->elastic condition concept-type)))

  cmr.search.models.query.ExistCondition
  (condition->elastic
    [{:keys [field]} _]
    {:exists {:field field}})

  cmr.search.models.query.MissingCondition
  (condition->elastic
    [{:keys [field]} _]
    {:missing {:field field}})

  cmr.search.models.query.NumericValueCondition
  (condition->elastic
    [{:keys [field value]} _]
    {:term {field value}})

  cmr.search.models.query.NumericRangeCondition
  (condition->elastic
    [{:keys [field min-value max-value]} _]
    (cond
      (and min-value max-value)
      {:range {field {:gte min-value :lte max-value}
               :execution "fielddata"}}

      min-value
      {:range {field {:gte min-value}
               :execution "fielddata"}}

      max-value
      {:range {field {:lte max-value}
               :execution "fielddata"}}

      :else
      (errors/internal-error! (m/nil-min-max-msg))))

  cmr.search.models.query.DateRangeCondition
  (condition->elastic
    [{:keys [field start-date end-date]} concept-type]
    (let [field (query-field->elastic-field field concept-type)
          from-value (if start-date (h/utc-time->elastic-time start-date) h/earliest-echo-start-date)
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
