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

(defn- extract-spatial-and-temporal-conditions
  "Returns a list of the spatial and temporal conditions in the query. Throws an exception if a
  parent of the spatial or temporal condition isn't expected."
  [query]
  (condition-extractor/extract-conditions
    query
    ;; Matches spatial or temporal conditions
    (fn [condition-path condition]
      (when (or (instance? SpatialCondition condition)
                (instance? TemporalCondition condition))
        ;; Validate that the parent of the spatial or temporal condition is acceptable
        (validate-path-to-condition query condition-path)
        true))))


(defn- convert-spatial-or-temporal-condition
  "Applies any necessary changes to spatial or temporal condition to make them work with granules"
  [condition]
  (if (instance? TemporalCondition condition)
    ;; Remove collection concept type and turn off limit to granules
    ;; so that the correct fields will be searched.
    ;; limit to granules is a collection applicable parameter.
    (-> condition
        (dissoc :concept-type)
        (assoc :limit-to-granules false))
    condition))

(defn- extract-spatial-operator
  "Determine whether spatial conditions should be ORed or ANDed together"
  [query]
  (let [conditions (get-in query [:condition :conditions])
        condition-is-shape? (->> conditions
                                 (map keys)
                                 flatten
                                 (filter #(= % :shape))
                                 seq)
        parent-group-operator (get-in query [:condition :operation])]
    (when condition-is-shape?
      parent-group-operator)))

(defn- combine-spatial-and-non-spatial-conditions
  "Helper function. If spatial conitions are ORed, combine them with the rest of
  the conditions appropriately."
  [spatial-conditions temporal-conditions collection-ids]
  (let [collection-id-conditions (q/string-conditions :collection-concept-id collection-ids true)
        ored-spatial-conditions (gc/or-conds spatial-conditions)]
    (if (seq collection-ids)
      (gc/and-conds (remove empty? [collection-id-conditions
                                    ored-spatial-conditions
                                    temporal-conditions]))
      ;; The results were empty so the granule count query doesn't need to find anything.
      q/match-none)))

(defn extract-granule-count-query
  "Extracts a query to find the number of granules per collection in the results from a collection query
  coll-query - The collection query
  results - the results of the collection query"
  [coll-query results]
  (let [collection-ids (query-results->concept-ids results)
        spatial-operator (extract-spatial-operator coll-query)
        spatial-temp-conds (->> coll-query
                                extract-spatial-and-temporal-conditions
                                (map convert-spatial-or-temporal-condition))
        spatial-conditions (->> spatial-temp-conds
                                (map :shape)
                                (map qm/->SpatialCondition))
        temporal-conditions (remove :shape spatial-temp-conds)
        combined-conditions (if (and (= spatial-operator :or)
                                     (seq spatial-conditions))
                              (combine-spatial-and-non-spatial-conditions
                               spatial-conditions temporal-conditions collection-ids)
                              spatial-temp-conds)
        condition (if (seq collection-ids)
                    (gc/and-conds (cons (q/string-conditions :collection-concept-id collection-ids true)
                                        spatial-temp-conds))
                    ;; The results were empty so the granule count query doesn't need to find anything.
                    q/match-none)]
    (q/query {:concept-type :granule
              :condition (if (= :or spatial-operator)
                           combined-conditions
                           condition)
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
         (extract-granule-count-query query)
         (query-execution/execute-query context)
         search-results->granule-counts
         (assoc query-results :granule-counts-map))))
