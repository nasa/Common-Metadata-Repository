(ns cmr.search.data.query-to-elastic
  "Defines protocols and functions to map from a query model to elastic search query"
  (:require [clojurewerkz.elastisch.query :as q]
            [clojure.string :as str]
            [cmr.search.models.query :as qm]
            [cmr.search.data.datetime-helper :as h]
            [cmr.common.services.errors :as errors]
            [cmr.search.data.messages :as m]
            [cmr.search.data.keywords-to-elastic :as k2e]))

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

(defn query-field->elastic-field
  "Returns the elastic field name for the equivalent query field name."
  [field concept-type]
  (get-in field-mappings [concept-type field] field))

(def query_string-reserved-characters-regex
  "Characters reserved for elastic query_string queries. These must be escaped."
  #"([+\-\[\]!\^:\\/{}\(\)\"]|&&)")

(defprotocol ConditionToElastic
  "Defines a function to map from a query to elastic search query"
  (condition->elastic
    [condition concept-type]
    "Converts a query model condition into the equivalent elastic search filter"))

(defn query->elastic
  "Converts a query model into an elastic search query"
  [query]
  (let [{:keys [concept-type condition keywords]} query
        core-query (condition->elastic condition concept-type)]
    (if-let [keywords (:keywords query)]
      ;; function_score query allows us to compute a custom relevance score for each document
      ;; matched by the primary query. The final document relevance is given by multiplying
      ;; a boosting term for each matching filter in a set of filters.
      {:function_score {:functions (k2e/keywords->boosted-elastic-filters keywords)
                        :query {:filtered {:query (q/match-all)
                                           :filter core-query}}}}
      {:filtered {:query (q/match-all)
                  :filter core-query}})))

(def sort-key-field->elastic-field
  "Submaps by concept type of the sort key fields given by the user to the exact elastic sort field to use.
  If a sort key is not in this map it means that it can be used directly with elastic."
  {:collection {:entry-title :entry-title.lowercase
                :provider :provider-id.lowercase
                :platform :platform-sn.lowercase
                :instrument :instrument-sn.lowercase
                :sensor :sensor-sn.lowercase
                :score :_score}
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
  (let [{:keys [concept-type sort-keys]} query
        concept-id-sort {:concept-id {:order "asc"}}
        specified-sort (map (fn [{:keys [order field]}]
                              {(get-in sort-key-field->elastic-field [concept-type field] (name field))
                               {:order order}})
                            sort-keys)]
    ;; Sorting within elastic if the sort keys match is essentially random. We add a globally unique
    ;; sort to the end of the specified sort keys so that sorting is always the same. This makes
    ;; paging and query results consistent.
    (concat specified-sort [concept-id-sort])))

(defn- range-condition->elastic
  "Convert a range condition to an elastic search condition. Execution
  should either by 'fielddata' or 'index'."
  [field min-value max-value execution]
  (cond
    (and min-value max-value)
    {:range {field {:gte min-value :lte max-value}
             :execution execution}}

    min-value
    {:range {field {:gte min-value}
             :execution execution}}

    max-value
    {:range {field {:lte max-value}
             :execution execution}}

    :else
    (errors/internal-error! (m/nil-min-max-msg))))

(extend-protocol ConditionToElastic
  cmr.search.models.query.ConditionGroup
  (condition->elastic
    [{:keys [operation conditions]} concept-type]
    ;; Performance enhancement: We should order the conditions within and/ors.
    {operation {:filters (map #(condition->elastic % concept-type) conditions)}})

  cmr.search.models.query.NestedCondition
  (condition->elastic
    [{:keys [path condition]} concept-type]
    {:nested {:path path
              :filter (condition->elastic condition concept-type)}})

  cmr.search.models.query.TextCondition
  (condition->elastic
    [{:keys [field query-str]} concept-type]
    (let [field (query-field->elastic-field field concept-type)]
      {:query {:query_string {:query (str/replace query-str
                                                  query_string-reserved-characters-regex
                                                  "\\\\$1")
                              :analyzer :whitespace
                              :default_field field
                              :default_operator :and}}}))

  cmr.search.models.query.StringCondition
  (condition->elastic
    [{:keys [field value case-sensitive? pattern?]} concept-type]
    (let [field (query-field->elastic-field field concept-type)
          field (if case-sensitive? field (str (name field) ".lowercase"))
          value (if case-sensitive? value (str/lower-case value))]
      (if pattern?
        {:query {:wildcard {field value}}}
        {:term {field value}})))

  cmr.search.models.query.StringsCondition
  (condition->elastic
    [{:keys [field values case-sensitive?]} concept-type]
    (let [field (query-field->elastic-field field concept-type)
          field (if case-sensitive? field (str (name field) ".lowercase"))
          values (if case-sensitive? values (map str/lower-case values))]
      {:terms {field values
               :execution "bool"}}))

  cmr.search.models.query.BooleanCondition
  (condition->elastic
    [{:keys [field value]} concept-type]
    (let [field (query-field->elastic-field field concept-type)]
      {:term {field value}}))

  cmr.search.models.query.NegatedCondition
  (condition->elastic
    [{:keys [condition]} concept-type]
    {:not (condition->elastic condition concept-type)})

  cmr.search.models.query.ScriptCondition
  (condition->elastic
    [{:keys [name params]} concept-type]
    {:script {:script name
              :params params
              :lang "native"}})

  cmr.search.models.query.ExistCondition
  (condition->elastic
    [{:keys [field]} concept-type]
    {:exists {:field (query-field->elastic-field field concept-type)}})

  cmr.search.models.query.MissingCondition
  (condition->elastic
    [{:keys [field]} concept-type]
    {:missing {:field (query-field->elastic-field field concept-type)}})

  cmr.search.models.query.NumericValueCondition
  (condition->elastic
    [{:keys [field value]} concept-type]
    {:term {(query-field->elastic-field field concept-type) value}})

  cmr.search.models.query.NumericRangeCondition
  (condition->elastic
    [{:keys [field min-value max-value]} concept-type]
    (range-condition->elastic (query-field->elastic-field field concept-type)
                              min-value max-value "fielddata"))

  cmr.search.models.query.NumericRangeIntersectionCondition
  (condition->elastic
    [{:keys [min-field max-field min-value max-value]} concept-type]
    {:or [(range-condition->elastic (query-field->elastic-field min-field concept-type)
                                    min-value
                                    max-value
                                    "fielddata")
          (range-condition->elastic (query-field->elastic-field max-field concept-type)
                                    min-value
                                    max-value
                                    "fielddata")
          {:and [(range-condition->elastic (query-field->elastic-field min-field concept-type)
                                           nil
                                           min-value
                                           "fielddata")
                 (range-condition->elastic (query-field->elastic-field max-field concept-type)
                                           max-value
                                           nil
                                           "fielddata")]}]})

  cmr.search.models.query.StringRangeCondition
  (condition->elastic
    [{:keys [field start-value end-value]} concept-type]
    (range-condition->elastic (query-field->elastic-field field concept-type)
                              start-value end-value "index"))

  cmr.search.models.query.DateRangeCondition
  (condition->elastic
    [{:keys [field start-date end-date]} concept-type]
    (let [field (query-field->elastic-field field concept-type)
          from-value (if start-date (h/utc-time->elastic-time start-date) h/earliest-echo-start-date)
          value {:from from-value}
          value (if end-date (assoc value :to (h/utc-time->elastic-time end-date)) value)]
      {:range { field value }}))

  cmr.search.models.query.DateValueCondition
  (condition->elastic
    [{:keys [field value]} concept-type]
    {:term {field (h/utc-time->elastic-time value)}})

  cmr.search.models.query.MatchAllCondition
  (condition->elastic
    [_ _]
    {:match_all {}})

  cmr.search.models.query.MatchNoneCondition
  (condition->elastic
    [_ _]
    {:term {:match_none "none"}}))
