(ns cmr.search.services.query-execution.granule-counts-results-feature
  "This enables the :include-granule-counts feature for collection search results. When it is enabled
  collection search results will include the number of granules in each collection that would match
  the same query. It is currently limited to spatial and temporal conditions within collection queries.
  Other types of conditions will not be included in limiting the granule counts."
  (:require
   [clojure.data :as data]
   [clojure.string :as string]
   [cmr.common-app.services.search.complex-to-simple :as c2s]
   [cmr.common-app.services.search.elastic-search-index :as idx]
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.query-execution :as query-execution]
   [cmr.common-app.services.search.query-model :as q]
   [cmr.common-app.services.search.query-to-elastic :as q2e]
   [cmr.common.services.errors :as errors]
   [cmr.search.models.query :as qm]
   [cmr.search.services.acl-service :as acl-service]
   [cmr.search.services.query-walkers.condition-extractor :as condition-extractor])
  (:import [cmr.common_app.services.search.query_model
            Query
            ConditionGroup]
           [cmr.search.models.query
            SpatialCondition
            TemporalCondition]))

(defmulti query-results->concept-ids
  "Returns the concept ids from the query results. It is expected that results handlers will
  implement this multi-method"
  (fn [results]
    (let [result-format (:result-format results)]
      ;; Versioned result-format case like {:format :umm-json-results, :version 1.10}
      (if-let [format (:format result-format)]
        format
        result-format))))

(defn- valid-parent-condition?
  "The granule count query extractor can extract spatial and temporal conditions from a collection
  query. It only supports extracting spatial and temporal conditions that aren't negated. Returns true if the parent condition is allowed"
  [condition]
  (let [cond-type (type condition)]
    (or (= cond-type Query)
        (= cond-type ConditionGroup))))

(defn- validate-path-to-condition
  "Validates that the path to the condition we are extracting from the query doesn't contain
  any of the conditions that change it's meaning. This is mainly that the condition is AND'd from
  the top level and not negated. Throws an exception if one is encountered."
  [query condition-path]
  (loop [conditions condition-path]
    (when-let [condition (first conditions)]
      (when-not (valid-parent-condition? condition)
        (errors/internal-error!
          (str "Granule query extractor found spatial or temporal condition within an unexpected "
               "condition. Query:" (pr-str query))))
      (recur (rest conditions)))))

(defn- extract-conditions
  "Returns a list of conditions with the given condition type in the query.
  Throws an exception if a parent of the condition isn't expected."
  [query conditionType]
  (condition-extractor/extract-conditions
    query
    ;; Matches conditions by condition type
    (fn [condition-path condition]
      (when (instance? conditionType condition)
        ;; Validate that the parent of the condition is acceptable
        (validate-path-to-condition query condition-path)
        true))))

(defn- extract-spatial-conditions
  "Returns a list of the spatial conditions in the query."
  [query]
  (extract-conditions query SpatialCondition))

(defn- sanitize-temporal-condition
  "Returns the temporal condition with collection related info cleared out."
  [condition]
  (-> condition
      (dissoc :concept-type)
      (assoc :limit-to-granules false)))

(defn- extract-temporal-conditions
  "Returns a list of the temporal conditions in the query. "
  [query]
  (->> (extract-conditions query TemporalCondition)
       (map sanitize-temporal-condition)))

(defn- has-spatial-or-option?
  "Returns true if the spatial conditions should be ORed together"
  [context]
  (when-let [query-string (:query-string context)]
    (let [parameters (string/split query-string #"\?|&")
          spatial-param-regex #"(options%5Bspatial%5D%5Bor%5D=true|options\[spatial\]\[or\]=true)"]
      (when (some #(re-matches spatial-param-regex %) parameters)
        true))))

(defn- get-spatial-condition
  "Returns the spatial condition from collection query and spatial operator."
  [context coll-query]
  (let [spatial-conds (extract-spatial-conditions coll-query)]
    (when (seq spatial-conds)
      (if (has-spatial-or-option? context)
        (gc/or-conds spatial-conds)
        (gc/and-conds spatial-conds)))))

(defn- get-granule-count-condition
  "Returns the granule count query condition for the given collection ids and collection query."
  [context collection-ids coll-query]
  (if (seq collection-ids)
    (let [ids-condition (q/string-conditions :collection-concept-id collection-ids true)
          spatial-condition (get-spatial-condition context coll-query)
          temporal-conditions (extract-temporal-conditions coll-query)]
      (gc/and-conds
       (remove nil?
               (concat [ids-condition spatial-condition]
                       temporal-conditions))))
    ;; The results were empty so the granule count query doesn't need to find anything.
    q/match-none))

(defn extract-granule-count-query
  "Extracts a query to find the number of granules per collection in the results from a collection query
  coll-query - The collection query
  results - the results of the collection query"
  [context coll-query results]
  (let [collection-ids (query-results->concept-ids results)
        condition (get-granule-count-condition context collection-ids coll-query)]
    (q/query {:concept-type :granule
              :condition condition
              ;; We don't need any results
              :page-size 0
              :result-format :query-specified
              :result-fields []
              :aggregations {:granule-counts-by-collection-id
                             {:terms {:field (q2e/query-field->elastic-field
                                              :collection-concept-id :granule)
                                      :size (count collection-ids)}}}})))

(defn search-results->granule-counts
  "Extracts the granule counts per collection id in a map from the search results from ElasticSearch"
  [elastic-results]
  (let [aggs (get-in elastic-results [:aggregations :granule-counts-by-collection-id :buckets])]
    (into {} (for [{collection-id :key num-granules :doc_count} aggs]
               [collection-id num-granules]))))

;; This find granule counts per collection.
(defmethod query-execution/post-process-query-result-feature :granule-counts
  [context query elastic-results query-results feature]
  (if (zero? (count (query-results->concept-ids query-results)))
    query-results
    (->> query-results
         (extract-granule-count-query context query)
         (query-execution/execute-query context)
         search-results->granule-counts
         (assoc query-results :granule-counts-map))))
