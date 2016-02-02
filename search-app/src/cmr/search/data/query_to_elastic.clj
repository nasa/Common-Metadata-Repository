(ns cmr.search.data.query-to-elastic
  "Defines protocols and functions to map from a query model to elastic search query"
  (:require [clojure.string :as str]
            ;; require it so it will be available
            [cmr.search.data.query-order-by-expense]
            [cmr.common.services.errors :as errors]
            [cmr.search.data.keywords-to-elastic :as k2e]
            [cmr.common-app.services.search.query-model :as q]
            [cmr.common-app.services.search.query-order-by-expense :as query-expense]
            [cmr.common-app.services.search.query-to-elastic :as q2e]
            [cmr.common-app.services.search.complex-to-simple :as c2s]))


(defmethod q2e/concept-type->field-mappings :collection
  [_]
  {:provider :provider-id
   :version :version-id
   :project :project-sn2
   :updated-since :revision-date2
   :two-d-coordinate-system-name :two-d-coord-name
   :platform :platform-sn
   :instrument :instrument-sn
   :sensor :sensor-sn
   :revision-date :revision-date2})

(defmethod q2e/concept-type->field-mappings :granule
  [_]
  {:provider :provider-id
   :producer-granule-id :producer-gran-id
   :updated-since :revision-date
   :platform :platform-sn
   :instrument :instrument-sn
   :sensor :sensor-sn
   :project :project-refs})

(defn- keywords-in-condition
  "Returns a list of keywords if the condition contains a keyword condition or nil if not."
  [condition]
  (when (not= (type condition) cmr.common_app.services.search.query_model.NegatedCondition)
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

(defn query->elastic
  "Converts a query model into an elastic search query"
  [query]
  (let [boosts (:boosts query)
        {:keys [concept-type condition keywords]} (query-expense/order-conditions query)
        core-query (q2e/condition->elastic condition concept-type)]
    (if-let [keywords (keywords-in-query query)]
      ;; function_score query allows us to compute a custom relevance score for each document
      ;; matched by the primary query. The final document relevance is given by multiplying
      ;; a boosting term for each matching filter in a set of filters.
      {:function_score {:score_mode :multiply
                        :functions (k2e/keywords->boosted-elastic-filters keywords boosts)
                        :query {:filtered {:query (q/match-all)
                                           :filter core-query}}}}
      (if boosts
        (errors/throw-service-errors :bad-request ["Relevance boosting is only supported for keyword queries"])
        {:filtered {:query (q/match-all)
                    :filter core-query}}))))

(defmethod q2e/concept-type->sort-key-map :collection
  [_]
  {:entry-title :entry-title.lowercase
   :entry-id :entry-id.lowercase
   :provider :provider-id.lowercase
   :platform :platform-sn.lowercase
   :instrument :instrument-sn.lowercase
   :sensor :sensor-sn.lowercase
   :score :_score
   :revision-date :revision-date2})

(defmethod q2e/concept-type->sort-key-map :tag
  [_]
  {:namespace :namespace.lowercase
   :value :value.lowercase})

(defmethod q2e/concept-type->sort-key-map :granule
  [_]
  {:provider :provider-id.lowercase
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
   :project :project-refs.lowercase})

(defmethod q2e/concept-type->sub-sort-fields :collection
  [_]
  [{:concept-seq-id {:order "asc"}} {:revision-id {:order "desc"}}])

(defmethod q2e/concept-type->sub-sort-fields :granule
  [_]
  [{:concept-seq-id {:order "asc"}}])

;; Collections will default to the keyword sort if they have no sort specified and search by keywords
(defmethod q2e/query->sort-params :collection
  [query]
  (let [{:keys [concept-type sort-keys]} query
        ;; If the sort keys are given as parameters then keyword-sort will not be used.
        keyword-sort (when (keywords-in-query query)
                       [{:_score {:order :desc}}])
        specified-sort (q2e/sort-keys->elastic-sort concept-type sort-keys)
        default-sort (q2e/sort-keys->elastic-sort concept-type (q/default-sort-keys concept-type))]
    (concat (or specified-sort keyword-sort default-sort) (q2e/concept-type->sub-sort-fields concept-type))))

(extend-protocol c2s/ComplexQueryToSimple
  cmr.search.models.query.CollectionQueryCondition
  (reduce-query-condition
    [condition context]
    (update-in condition [:condition] c2s/reduce-query-condition context)))
