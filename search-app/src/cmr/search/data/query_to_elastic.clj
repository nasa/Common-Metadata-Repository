(ns cmr.search.data.query-to-elastic
  "Defines protocols and functions to map from a query model to elastic search query"
  (:require [clojurewerkz.elastisch.query :as q]
            [clojure.string :as str]
            [cmr.search.models.query :as qm]
            [cmr.search.data.datetime-helper :as h]
            [cmr.common.services.errors :as errors]
            [cmr.search.data.messages :as m]
            [cmr.search.data.keywords-to-elastic :as k2e]
            [cmr.common.config :as cfg]
            [cmr.search.data.query-order-by-expense :as query-expense]))

(def numeric-range-execution-mode (cfg/config-value-fn :numeric-range-execution-mode "fielddata"))
(def numeric-range-use-cache (cfg/config-value-fn :numeric-range-use-cache "false" #(= "true" %)))

(def field-mappings
  "A map of fields in the query to the field name in elastic. Field names are excluded from this
  map if the query field name matches the field name in elastic search."
  {:collection {:provider :provider-id
                :version :version-id
                :project :project-sn2
                :updated-since :revision-date2
                :two-d-coordinate-system-name :two-d-coord-name
                :platform :platform-sn
                :instrument :instrument-sn
                :sensor :sensor-sn
                :revision-date :revision-date2}

   :tag {}

   :granule {:provider :provider-id
             :producer-granule-id :producer-gran-id
             :updated-since :revision-date
             :platform :platform-sn
             :instrument :instrument-sn
             :sensor :sensor-sn
             :project :project-refs}})

(defn- query-field->elastic-field
  "Returns the elastic field name for the equivalent query field name."
  [field concept-type]
  (get-in field-mappings [concept-type field] field))

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

(defn- keywords-in-condition
  "Returns a list of keywords if the condition contains a keyword condition or nil if not."
  [condition]
  (when (not= (type condition) cmr.search.models.query.NegatedCondition)
    (or (when (= :keyword (:field condition))
          (str/split (str/lower-case (:query-str condition)) #" "))
        ;; Call this function recursively on nested conditions, e.g., AND or OR conditions.
        (when-let [conds (:conditions condition)]
          (some #(keywords-in-condition %1) conds))
        ;; Call this function recursively for a single nested condition.
        (when-let [con (:condition condition)] (keywords-in-condition con)))))

(defn- keywords-in-query
  "Returns a list of keywords if the query contains a keyword condition or nil if not.
  Used to set sort and use function score for keyword queries."
  [query]
  (keywords-in-condition (:condition query)))

(defprotocol ConditionToElastic
  "Defines a function to map from a query to elastic search query"
  (condition->elastic
    [condition concept-type]
    "Converts a query model condition into the equivalent elastic search filter"))

(defn query->elastic
  "Converts a query model into an elastic search query"
  [query]
  (let [boosts (:boosts query)
        {:keys [concept-type condition keywords]} (query-expense/order-conditions query)
        core-query (condition->elastic condition concept-type)]
    (if-let [keywords (keywords-in-query query)]
      ;; function_score query allows us to compute a custom relevance score for each document
      ;; matched by the primary query. The final document relevance is given by multiplying
      ;; a boosting term for each matching filter in a set of filters.
      {:function_score {:score_mode :multiply
                        :functions (k2e/keywords->boosted-elastic-filters keywords boosts true) ;; TOOD change true to use boosts[use_defaults] parameter
                        :query {:filtered {:query (q/match-all)
                                           :filter core-query}}}}
      ;; TODO add validation here to make sure boosts are not specified
      (if boosts
        (errors/throw-service-errors :bad-request ["Boosting is only supported for keyword queries"])
        {:filtered {:query (q/match-all)
                  :filter core-query}}))))

(def sort-key-field->elastic-field
  "Submaps by concept type of the sort key fields given by the user to the exact elastic sort field to use.
  If a sort key is not in this map it means that it can be used directly with elastic."
  {:collection {:entry-title :entry-title.lowercase
                :entry-id :entry-id.lowercase
                :provider :provider-id.lowercase
                :platform :platform-sn.lowercase
                :instrument :instrument-sn.lowercase
                :sensor :sensor-sn.lowercase
                :score :_score
                :revision-date :revision-date2}
   :tag {:namespace :namespace.lowercase
         :value :value.lowercase}
   :granule {:provider :provider-id.lowercase
             :entry-title :entry-title.lowercase
             :short-name :short-name.lowercase
             :version :version-id.lowercase
             :granule-ur :granule-ur.lowercase2
             :producer-granule-id :producer-gran-id.lowercase2
             :readable-granule-name :readable-granule-name-sort2
             :data-size :size
             :platform :platform-sn.lowercase
             :instrument :instrument-sn.lowercase
             :sensor :sensor-sn.lowercase
             :project :project-refs.lowercase}})

(defn- sort-keys->elastic-sort
  "Converts sort keys into the proper elastic sort condition."
  [concept-type sort-keys]
  (seq (map (fn [{:keys [order field]}]
              {(get-in sort-key-field->elastic-field [concept-type field] (name field))
                {:order order}})
            sort-keys)))

(def sub-sorts
  "Sub sorting applied per concept type to preserve paging, etc."
  {:collection [{:concept-seq-id {:order "asc"}} {:revision-id {:order "desc"}}]
   :granule [{:concept-seq-id {:order "asc"}}]
   :tag [{:concept-id {:order "asc"}}]})

(defn query->sort-params
  "Converts a query into the elastic parameters for sorting results"
  [query]
  (let [{:keys [concept-type sort-keys]} query
        ;; If the sort keys are given as parameters then keyword-sort will not be used.
        keyword-sort (when (keywords-in-query query)
                       [{:_score {:order :desc}}])
        specified-sort (sort-keys->elastic-sort concept-type sort-keys)
        default-sort (sort-keys->elastic-sort concept-type (qm/default-sort-keys concept-type))]
    ;; Sorting within elastic if the sort keys match is essentially random. We add a globally unique
    ;; sort to the end of the specified sort keys so that sorting is always the same. This makes
    ;; paging and query results consistent.
    (concat (or specified-sort keyword-sort default-sort) (sub-sorts concept-type))))

(defn field->lowercase-field
  "Maps a field name to the name of the lowercase field to use within elastic.
  If a mapping is not present it defaults the lowercase field as <field>.lowercase"
  [field]
  (or ((if (string? field) (keyword field) field)
       {:granule-ur "granule-ur.lowercase2"
        :producer-gran-id "producer-gran-id.lowercase2"})
      (str (name field) ".lowercase")))

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
       {:range {field {greater-than min-value less-than max-value}
                :execution execution
                :_cache use-cache}}

       min-value
       {:range {field {greater-than min-value}
                :execution execution
                :_cache use-cache}}

       max-value
       {:range {field {less-than max-value}
                :execution execution
                :_cache use-cache}}

       :else
       (errors/internal-error! (m/nil-min-max-msg))))))

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
      {:query {:query_string {:query (escape-query-string query-str)
                              :analyzer :whitespace
                              :default_field field
                              :default_operator :and}}}))

  cmr.search.models.query.StringCondition
  (condition->elastic
    [{:keys [field value case-sensitive? pattern?]} concept-type]
    (let [field (query-field->elastic-field field concept-type)
          field (if case-sensitive? field (field->lowercase-field field))
          value (if case-sensitive? value (str/lower-case value))]
      (if pattern?
        {:query {:wildcard {field value}}}
        {:term {field value}})))

  cmr.search.models.query.StringsCondition
  (condition->elastic
    [{:keys [field values case-sensitive?]} concept-type]
    (let [field (query-field->elastic-field field concept-type)
          field (if case-sensitive? field (field->lowercase-field field))
          values (if case-sensitive? values (map str/lower-case values))]
      {:terms {field values
               :execution "plain"}}))

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
    [{:keys [field min-value max-value exclusive?]} concept-type]
    (range-condition->elastic
      (query-field->elastic-field field concept-type)
      min-value max-value (numeric-range-execution-mode) (numeric-range-use-cache) exclusive?))


  cmr.search.models.query.NumericRangeIntersectionCondition
  (condition->elastic
    [{:keys [min-field max-field min-value max-value]} concept-type]
    {:or [(range-condition->elastic (query-field->elastic-field min-field concept-type)
                                    min-value
                                    max-value
                                    (numeric-range-execution-mode)
                                    (numeric-range-use-cache))
          (range-condition->elastic (query-field->elastic-field max-field concept-type)
                                    min-value
                                    max-value
                                    (numeric-range-execution-mode)
                                    (numeric-range-use-cache))
          {:and [(range-condition->elastic (query-field->elastic-field min-field concept-type)
                                           nil
                                           min-value
                                           (numeric-range-execution-mode)
                                           (numeric-range-use-cache))
                 (range-condition->elastic (query-field->elastic-field max-field concept-type)
                                           max-value
                                           nil
                                           (numeric-range-execution-mode)
                                           (numeric-range-use-cache))]}]})

  cmr.search.models.query.StringRangeCondition
  (condition->elastic
    [{:keys [field start-value end-value exclusive?]} concept-type]
    (range-condition->elastic (query-field->elastic-field field concept-type)
                              start-value end-value "index" true exclusive?))

  cmr.search.models.query.DateRangeCondition
  (condition->elastic
    [{:keys [field start-date end-date exclusive?]} concept-type]
    (let [field (query-field->elastic-field field concept-type)
          from-value (if start-date
                       (h/utc-time->elastic-time start-date)
                       h/earliest-start-date-elastic-time)
          end-value (when end-date (h/utc-time->elastic-time end-date))]
      (range-condition->elastic
        field from-value end-value (numeric-range-execution-mode) (numeric-range-use-cache) exclusive?)))

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
