(ns cmr.search.services.query-execution.has-granules-revised-at-feature
  "Supports the has_granules_revised_at query parameter for collections. Returns collections
  which have any granules that were created or updated within the time ranges specified by the
  has_granules_revised_at time range(s).

  This works exactly the same as the has_granules_created_at feature, but uses a different
  parameter to search for relevant granules."
  (:require
   [clojure.set :as set]
   [cmr.common-app.services.search.query-execution :as query-execution]
   [cmr.search.services.query-execution.multi-part-query-feature-common :as mp-query-common]))

(defmethod query-execution/pre-process-query-result-feature :has-granules-revised-at
  [context query feature]
  (let [first-coll-query (mp-query-common/collection-pre-query query :has-granules-revised-at)
        collection-results (:items (query-execution/execute-query context first-coll-query))
        collection-concept-ids (map :concept-id collection-results)
        granule-query (mp-query-common/generate-granule-query context query collection-concept-ids)
        results (query-execution/execute-query context granule-query)
        concept-ids (map :key (get-in results [:aggregations :collections :buckets]))]
    (assoc query
           :condition
           (mp-query-common/generate-collection-query-condition
            context query concept-ids :has-granules-revised-at))))
