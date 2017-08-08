(ns cmr.search.services.query-execution.has-granules-created-at-feature
  "Supports the has_granules_created_at query parameter for collections. Returns collections
  which have any granules that were created within the time ranges specified by the
  has_granules_created_at time range(s).

  Supporting the feature requires two queries
  1.) Perform a granule search for any granules that match the passed in query parameters and have
  a created_at in the has_granules_created_at time ranges.
  2.) Return the full search results for those collection-ids from the prior query."
  (:require
   [clojure.set :as set]
   [cmr.common-app.services.search.group-query-conditions :as group-query-conditions]
   [cmr.common-app.services.search.params :as common-params]
   [cmr.common-app.services.search.query-execution :as query-execution]
   [cmr.common-app.services.search.query-model :as query-model]
   [cmr.common-app.services.search.query-to-elastic :as query-to-elastic]
   [cmr.search.services.parameters.conversion :as parameters]
   [cmr.search.services.query-walkers.update-field-resolver :as update-field-resolver]))

(def query-aggregation-size
  "Page size for query aggregations. This should be large enough
  to contain all collections to allow searching for collections
  from granules."
  50000)

(defn has-granules-created-at-query
  "Generates a query to find any collection-concept-ids for granules that were created in the
  provided time range(s)."
  [condition]
  (query-model/query
   {:concept-type :granule
    :condition condition
    :page-size 0
    :result-format :query-specified
    :result-fields []
    :aggregations {:collections
                   {:terms {:size query-aggregation-size
                            :field (query-to-elastic/query-field->elastic-field
                                    :collection-concept-id :granule)}}}}))

(defn- generate-granule-query
  "Creates a query to get the granules based on the provided collection query."
  [query]
  (as-> query query
        ;; Change all collection field names to granule field names in the query
        (reduce (fn [upd-query [orig-field-key new-field-key]]
                  (update-field-resolver/rename-field upd-query orig-field-key new-field-key))
                query
                parameters/collection-to-granule-params)
        ;; Remove any collection specific fields from the query
        (reduce (fn [upd-query field-to-remove]
                  (update-field-resolver/remove-field upd-query field-to-remove))
                query
                parameters/collection-only-params)
        (has-granules-created-at-query (:condition query))))

(defn- generate-collection-query-condition
  "Takes the original query and the concept IDs from the granules query and constructs a new
  collection query condition to execute. If there are not any concept-ids we should return a
  match none condition."
  [context query concept-ids]
  (let [concept-ids-condition (when (seq concept-ids)
                                (common-params/parameter->condition context :collection :concept-id
                                                                    concept-ids nil))
        ;; Remove the has-granules-created-at query condition
        query (update-field-resolver/remove-field query :has-granules-created-at)]
    (if (seq concept-ids)
      (group-query-conditions/and-conds [(:condition query) concept-ids-condition])
      query-model/match-none)))

(defmethod query-execution/pre-process-query-result-feature :has-granules-created-at
  [context query feature]
  (let [granule-query (generate-granule-query query)
        results (query-execution/execute-query context granule-query)
        concept-ids (map :key (get-in results [:aggregations :collections :buckets]))]
    (assoc query :condition (generate-collection-query-condition context query concept-ids))))
