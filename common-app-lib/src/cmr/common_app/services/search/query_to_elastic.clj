(ns cmr.common-app.services.search.query-to-elastic
  "Defines protocols and functions to map from a query model to elastic search query"
  (:require
   [clojure.string :as str]
   [clojurewerkz.elastisch.query :as q]
   [cmr.common-app.services.search.datetime-helper :as h]
   [cmr.common-app.services.search.messages :as m]
   [cmr.common-app.services.search.query-model :as qm]
   [cmr.common-app.services.search.query-order-by-expense :as query-expense]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.services.errors :as errors]))

(defconfig numeric-range-execution-mode
  "Defines the execution mode to use for numeric ranges in an elasticsearch search"
  {:default "fielddata"})

(defconfig numeric-range-use-cache
  "Indicates whether the numeric range should use the field data cache or not."
  {:type Boolean
   :default false})

(defmulti concept-type->field-mappings
  "Returns a map of fields in the query to the field name in elastic. Field names are excluded from
  this map if the query field name matches the field name in elastic search."
  (fn [concept-type]
    concept-type))

(defmethod concept-type->field-mappings :default
  [_]
  ;; No field mappings specified by default
  {})

(defmulti elastic-field->query-field-mappings
  "A map of fields in elasticsearch to their query model field names. Field names are
  excluded from this map if the CMR query field name matches the field name in elastic search."
  (fn [concept-type]
    concept-type))

(defmethod elastic-field->query-field-mappings :default
  [_]
  ;; No field mappings specified by default
  {})

(defn query-field->elastic-field
  "Returns the elastic field name for the equivalent query field name."
  [field concept-type]
  (get (concept-type->field-mappings concept-type) (keyword field) field))

(defn- elastic-field->query-field
  "Returns the query field name for the equivalent elastic field name."
  [field concept-type]
  (get (elastic-field->query-field-mappings concept-type) field field))

(def ^:private query-string-reserved-characters-regex
  "Characters reserved for elastic query_string queries. These must be escaped."
  #"([+\-\[\]!\^:/{}\\\(\)\"\~]|&&|\|\|)")

(defn- fix-double-escapes
  "Fixes special characters that were doubly escaped by the reserved character escaping."
  [query]
  (-> query
      (str/replace "\\\\?" "\\?")
      (str/replace "\\\\*" "\\*")
      (str/replace "\\\\&&" "\\&&")
      (str/replace "\\\\||" "\\||")))

(defn escape-query-string
  "Takes the provided string and escapes any special characters reserved within elastic
  query_string queries."
  [query-str]
  (fix-double-escapes
    (str/replace query-str
                 query-string-reserved-characters-regex
                 "\\\\$1")))

(defprotocol ConditionToElastic
  "Defines a function to map from a query to elastic search query"
  (condition->elastic
    [condition concept-type]
    "Converts a query model condition into the equivalent elastic search filter"))

(defmulti query->elastic
  "Converts a query model into an elastic search query"
  :concept-type)

(defmethod query->elastic :default
  [query]
  (let [{:keys [concept-type condition]} (query-expense/order-conditions query)
        core-query (condition->elastic condition concept-type)]
    {:query {:bool {:must (q/match-all)
                    :filter core-query}}}))

(defmethod query->elastic :autocomplete
  [query]
  (let [{:keys [concept-type condition]} (query-expense/order-conditions query)
        core-query (condition->elastic condition concept-type)]
    (if (:filter core-query)
      {:query {:filtered core-query}}
      {:query core-query})))

(defmulti concept-type->sort-key-map
  "Returns a submaps for the concept type of the sort key fields given by the user to the elastic
  sort field to use. If a sort key is not in this map it means that it can be used directly with
  elastic."
  (fn [concept-type]
    concept-type))

(defmethod concept-type->sort-key-map :default
  [_]
  {})

(defmulti concept-type->sub-sort-fields
  "Returns a set of fields to append to every search to preserve paging, etc."
  (fn [concept-type]
    concept-type))

(defmethod concept-type->sub-sort-fields :default
  [concept-type]
  [{(query-field->elastic-field :concept-id concept-type) {:order "asc"}}])

(defn sort-keys->elastic-sort
  "Converts sort keys into the proper elastic sort condition."
  [concept-type sort-keys]
  (let [sort-key-map (concept-type->sort-key-map concept-type)]
    (seq (map (fn [{:keys [order field]}]
                {(get sort-key-map field (name field))
                 {:order order}})
              sort-keys))))

(defmulti query->sort-params
  "Converts a query into the elastic parameters for sorting results"
  :concept-type)

(defmethod query->sort-params :default
  [query]
  (let [{:keys [concept-type sort-keys]} query
        specified-sort (sort-keys->elastic-sort concept-type sort-keys)
        default-sort (sort-keys->elastic-sort concept-type (qm/default-sort-keys concept-type))]
    ;; Sorting within elastic if the sort keys match is essentially random. We add a globally unique
    ;; sort to the end of the specified sort keys so that sorting is always the same. This makes
    ;; paging and query results consistent.
    (concat (or specified-sort default-sort) (concept-type->sub-sort-fields concept-type))))

(defmulti field->lowercase-field-mappings
  "Mapping of query model field names to Elasticsearch lowercase field names."
  (fn [concept-type]
    concept-type))

(defmethod field->lowercase-field-mappings :default
  [_]
  ;; No field mappings specified by default
  {})

(defn field->lowercase-field
  "Maps a field name to the name of the lowercase field to use within elastic.
  If a mapping is not present it defaults the lowercase field as <field>-lowercase"
  [concept-type field]
  (get (field->lowercase-field-mappings concept-type) (keyword field)
       (str (name field) "-lowercase")))

(defn- range-condition->elastic
  "Convert a range condition to an elastic search condition. Execution
  should either by 'fielddata' or 'index'."
  ([field min-value max-value execution]
   (range-condition->elastic field min-value max-value execution true))
  ([field min-value max-value execution use-cache]
   (range-condition->elastic field min-value max-value execution use-cache false))
  ([field min-value max-value execution use-cache exclusive?]
   (let [[greater-than less-than] (if exclusive?
                                    [:gt :lt]
                                    [:gte :lte])]
     (cond
       (and min-value max-value)
       {:range {field {greater-than min-value less-than max-value}}}

       min-value
       {:range {field {greater-than min-value}}}

       max-value
       {:range {field {less-than max-value}}}

       :else
       (errors/internal-error! (m/nil-min-max-msg))))))

(extend-protocol ConditionToElastic
  cmr.common_app.services.search.query_model.ConditionGroup
  (condition->elastic
   [{:keys [operation conditions]} concept-type]
   ;; Performance enhancement: We should order the conditions within and/ors.
   (if (= :and operation)
     {:bool {:must (map #(condition->elastic % concept-type) conditions)}}
     {:bool {:should (map #(condition->elastic % concept-type) conditions)
             :minimum_should_match 1}}))

  cmr.common_app.services.search.query_model.NestedCondition
  (condition->elastic
   [{:keys [path condition]} concept-type]
   {:nested {:path path
             :query {:bool {:filter (condition->elastic condition concept-type)}}}})

  cmr.common_app.services.search.query_model.TextCondition
  (condition->elastic
   [{:keys [field query-str]} concept-type]
   (let [elastic-field (query-field->elastic-field field concept-type)]
     ;; For keyword phrase search with wildcard, we have to use span query.
     (if (and (= :keyword-phrase field) (= :collection concept-type))
       {:span_near
         {:clauses [{:span_multi
                      {:match
                        {:wildcard
                          {elastic-field (escape-query-string query-str)}}}}]
          :slop 0
          :in_order true}}
       {:query_string {:query (escape-query-string query-str)
                       :analyzer :whitespace
                       :default_field elastic-field
                       :default_operator :and}})))

  cmr.common_app.services.search.query_model.StringCondition
  (condition->elastic
   [{:keys [field value case-sensitive? pattern?]} concept-type]
   (let [field (if case-sensitive?
                 (query-field->elastic-field field concept-type)
                 (field->lowercase-field concept-type field))
         value (if case-sensitive? value (str/lower-case value))]
     (if pattern?
       {:wildcard {field value}}
       {:term {field value}})))

  cmr.common_app.services.search.query_model.StringsCondition
  (condition->elastic
   [{:keys [field values case-sensitive?]} concept-type]
   (let [field (if case-sensitive?
                 (query-field->elastic-field field concept-type)
                 (field->lowercase-field concept-type field))
         values (if case-sensitive? values (map str/lower-case values))]
     {:terms {field values}}))

  cmr.common_app.services.search.query_model.BooleanCondition
  (condition->elastic
   [{:keys [field value]} concept-type]
   (let [field (query-field->elastic-field field concept-type)]
     {:term {field value}}))

  cmr.common_app.services.search.query_model.NegatedCondition
  (condition->elastic
   [{:keys [condition]} concept-type]
   {:bool {:must_not (condition->elastic condition concept-type)}})

  cmr.common_app.services.search.query_model.ScriptCondition
  (condition->elastic
   [{:keys [source lang params]} concept-type]
   {:script {:script {:source source
                      :params params
                      :lang lang}}})

  cmr.common_app.services.search.query_model.ExistCondition
  (condition->elastic
   [{:keys [field]} concept-type]
   {:exists {:field (query-field->elastic-field field concept-type)}})

  cmr.common_app.services.search.query_model.MissingCondition
  (condition->elastic
   [{:keys [field]} concept-type]
   {:bool {:must_not {:exists {:field (query-field->elastic-field field concept-type)}}}})

  cmr.common_app.services.search.query_model.NumericValueCondition
  (condition->elastic
   [{:keys [field value]} concept-type]
   {:term {(query-field->elastic-field field concept-type) value}})

  cmr.common_app.services.search.query_model.NumericRangeCondition
  (condition->elastic
   [{:keys [field min-value max-value exclusive?]} concept-type]
   (range-condition->elastic
    (query-field->elastic-field field concept-type)
    min-value max-value (numeric-range-execution-mode) (numeric-range-use-cache) exclusive?))

  cmr.common_app.services.search.query_model.NumericRangeIntersectionCondition
  (condition->elastic
   [{:keys [min-field max-field min-value max-value]} concept-type]
   {:bool
    {:should [(range-condition->elastic (query-field->elastic-field min-field concept-type)
                                        min-value
                                        max-value
                                        (numeric-range-execution-mode)
                                        (numeric-range-use-cache))
              (range-condition->elastic (query-field->elastic-field max-field concept-type)
                                        min-value
                                        max-value
                                        (numeric-range-execution-mode)
                                        (numeric-range-use-cache))
              {:bool
               {:must [(range-condition->elastic (query-field->elastic-field min-field concept-type)
                                                 nil
                                                 min-value
                                                 (numeric-range-execution-mode)
                                                 (numeric-range-use-cache))
                       (range-condition->elastic (query-field->elastic-field max-field concept-type)
                                                 max-value
                                                 nil
                                                 (numeric-range-execution-mode)
                                                 (numeric-range-use-cache))]}}]
     :minimum_should_match 1}})

  cmr.common_app.services.search.query_model.StringRangeCondition
  (condition->elastic
   [{:keys [field start-value end-value exclusive?]} concept-type]
   (range-condition->elastic (query-field->elastic-field field concept-type)
                             start-value end-value "index" true exclusive?))

  cmr.common_app.services.search.query_model.DateRangeCondition
  (condition->elastic
   [{:keys [field start-date end-date exclusive?]} concept-type]
   (let [field (query-field->elastic-field field concept-type)
         from-value (if start-date
                      (h/utc-time->elastic-time start-date)
                      h/earliest-start-date-elastic-time)
         end-value (when end-date (h/utc-time->elastic-time end-date))]
     (range-condition->elastic
      field from-value end-value (numeric-range-execution-mode) (numeric-range-use-cache) exclusive?)))

  cmr.common_app.services.search.query_model.DateValueCondition
  (condition->elastic
   [{:keys [field value]} concept-type]
   {:term {field (h/utc-time->elastic-time value)}})

  cmr.common_app.services.search.query_model.MatchAllCondition
  (condition->elastic
   [_ _]
   {:match_all {}})

  cmr.common_app.services.search.query_model.MatchNoneCondition
  (condition->elastic
    [_ _]
    {:term {:match_none "none"}})

  cmr.common_app.services.search.query_model.MatchCondition
  (condition->elastic
   [{:keys [field value]} _]
   {:match {field value}})

  cmr.common_app.services.search.query_model.MatchBoolPrefixCondition
  (condition->elastic
    [{:keys [field value]} _]
    {:match_bool_prefix {field {:query value}}})
  
  cmr.common_app.services.search.query_model.MultiMatchCondition
  (condition->elastic
    [{:keys [query-type fields value opts]} _]
    {:multi_match (merge {:query value
                          :type query-type
                          :fields fields}
                         opts)}))
