(ns cmr.search.services.query-execution.has-granules-created-at-feature
  "Supports the has_granules_created_at query parameter for collections. Returns collections
  which have any granules that were created within the time ranges specified by the
  has_granules_created_at time range(s).

  Supporting the feature requires three queries
  1.) Perform a collection query to find only the collections that could potentially match based
  on the collection query parameters ignoring the granule time range.
  2.) Perform a granule search for any granules that match the passed in query parameters and
  collection concept-ids returned in the first query and have a created_at in the
  has_granules_created_at time ranges.
  3.) Return the full search results for those collection-ids returned by the granule query."
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

(defn- collection-pre-query
  "Returns a query that will get a list of collections that could potentially be matched. We do
  this to improve the performance of the granule query by reducing the potential indexes to be
  searched against."
  [query]
  (-> query
      (dissoc :result-features)
      (assoc :page-size :unlimited
             :result-fields [(query-to-elastic/query-field->elastic-field
                              :concept-id :collection)])
      query-model/query
      (update-field-resolver/remove-field :has-granules-created-at)))

(defn- generate-granule-query
  "Returns a query model to search for granules based on the provided collection query and
  collection concept IDs."
  [context query collection-concept-ids]
  (let [collection-concept-ids-condition (when (seq collection-concept-ids)
                                           (common-params/parameter->condition
                                            context :granule :collection-concept-id
                                            collection-concept-ids nil))]
    (if-not (seq collection-concept-ids)
      (query-model/query {:concept-type :granule :condition query-model/match-none})
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
            ;; Add collection concept IDs to query
            (group-query-conditions/and-conds [(:condition query) collection-concept-ids-condition])
            (has-granules-created-at-query query)))))

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
  (let [first-coll-query (collection-pre-query query)
        collection-results (:items (query-execution/execute-query context first-coll-query))
        collection-concept-ids (map :concept-id collection-results)
        granule-query (generate-granule-query context query collection-concept-ids)
        results (query-execution/execute-query context granule-query)
        concept-ids (map :key (get-in results [:aggregations :collections :buckets]))]
    (assoc query :condition (generate-collection-query-condition context query concept-ids))))
