(ns cmr.common-app.services.search.query-execution
  (:require
   [cmr.common-app.services.search.complex-to-simple :as c2s]
   [cmr.common-app.services.search.elastic-results-to-query-results :as rc]
   [cmr.common-app.services.search.elastic-search-index :as idx]
   [cmr.common-app.services.search.query-model :as qm]
   [cmr.common-app.services.search.related-item-resolver :as related-item-resolver]
   [cmr.common-app.services.search.results-model :as results]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.transmit.config :as tc]))

(defmulti add-acl-conditions-to-query
  "Adds conditions to the query to enforce ACLs."
  (fn [context query]
    (:concept-type query)))

(defmethod add-acl-conditions-to-query :default
  [_ query]
  query)

(defmulti query->execution-strategy
  "Returns the execution strategy to use for query execution"
  :concept-type)

;; The default query execution strategy is to use elasticsearch
(defmethod query->execution-strategy :default
  [_]
  :elasticsearch)

(defmulti pre-process-query-result-feature
  "Applies result feature changes to the query before it is executed. Should return the query with
  any changes necessary to apply the feature."
  (fn [context query feature]
    feature))

(defmethod pre-process-query-result-feature :default
  [context query feature]
  ; Does nothing by default
  query)

(defmulti post-process-query-result-feature
  "Processes the results found by the query to add additional information or other changes
  based on the particular feature enabled by the user."
  (fn [context query elastic-results query-results feature]
    feature))

(defmethod post-process-query-result-feature :default
  [context query elastic-results query-results feature]
  ; Does nothing by default
  query-results)

(defn pre-process-query-result-features
  "Applies each result feature change to the query before it is executed. Returns the updated query."
  [context query]
  (reduce (partial pre-process-query-result-feature context)
          query
          (:result-features query)))

(defn post-process-query-result-features
  "Processes result features that execute after a query results have been found."
  [context query elastic-results query-results]
  (reduce (partial post-process-query-result-feature context query elastic-results)
          query-results
          (:result-features query)))

(defmulti execute-query
  "Executes the query using the most appropriate mechanism"
  (fn [context query]
    (query->execution-strategy query)))

(defmulti concept-type-specific-query-processing
  "Performs processing on the context and the query specific to the concept type being searched"
  (fn [context query]
    (:concept-type query)))

(defmethod concept-type-specific-query-processing :default
  [context query]
  [context query])

(defmethod execute-query :elasticsearch
  [context query]
  (let [[context processed-query] (concept-type-specific-query-processing
                                   context query)
        processed-query (pre-process-query-result-features context processed-query)
        elastic-results (->> processed-query
                             (#(if (or (tc/echo-system-token? context) (:skip-acls? %))
                                 %
                                 (add-acl-conditions-to-query context %)))
                             (c2s/reduce-query context)
                             (idx/execute-query context))
        query-results (rc/elastic-results->query-results context processed-query elastic-results)]
    (post-process-query-result-features context query elastic-results query-results)))
