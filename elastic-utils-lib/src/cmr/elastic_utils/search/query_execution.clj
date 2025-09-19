(ns cmr.elastic-utils.search.query-execution
  "Defines a bunch of defmulti functions and their defaults to handle different stats of the
   query-execution. These will be extended by other files."
  (:require
   [cmr.common.log :as log :refer [debug]]
   [cmr.elastic-utils.search.es-index :as idx]
   [cmr.elastic-utils.search.es-results-to-query-results :as rc]
   [cmr.elastic-utils.search.query-transform :as c2s]
   [cmr.transmit.config :as tc]))

;; *************************************************************************************************

(defmulti add-acl-conditions-to-query
  "Adds conditions to the query to enforce ACLs."
  (fn [_context query]
    (:concept-type query)))

(defmethod add-acl-conditions-to-query :default
  [_ query]
  query)

;; *************************************************************************************************

(defmulti query->execution-strategy
  "Returns the execution strategy to use for query execution"
  :concept-type)

;; The default query execution strategy is to use elasticsearch
(defmethod query->execution-strategy :default
  [_]
  :elasticsearch)

;; *************************************************************************************************

(defmulti pre-process-query-result-feature
  "Applies result feature changes to the query before it is executed. Should return the query with
  any changes necessary to apply the feature."
  (fn [_context _query feature]
    feature))

(defmethod pre-process-query-result-feature :default
  [_context query _feature]
  ; Does nothing by default
  query)

;; ************************************
;; Related functions to above defmulti

(defn pre-process-query-result-features
  "Applies each result feature change to the query before it executes. Returns the updated query."
  [context query]
  (reduce (partial pre-process-query-result-feature context)
          query
          (:result-features query)))

;; *************************************************************************************************

(defmulti post-process-query-result-feature
  "Processes the results found by the query to add additional information or other changes
  based on the particular feature enabled by the user."
  (fn [_context _query _elastic-results _query-results feature]
    feature))

(defmethod post-process-query-result-feature :default
  [_context _query _elastic-results query-results _feature]
  ; Does nothing by default
  query-results)

;; ************************************
;; Related functions to above defmulti

(defn post-process-query-result-features
  "Processes result features that execute after a query results have been found."
  [context query elastic-results query-results]
  (reduce (partial post-process-query-result-feature context query elastic-results)
          query-results
          (:result-features query)))

;; *************************************************************************************************

(defmulti concept-type-specific-query-processing
  "Performs processing on the context and the query specific to the concept type being searched"
  (fn [_context query]
    (:concept-type query)))

(defmethod concept-type-specific-query-processing :default
  [context query]
  [context query])

;; *************************************************************************************************

(defmulti execute-query
  "Executes the query using the most appropriate mechanism"
  (fn [_context query]
    (query->execution-strategy query)))

(def empty-es-results
  "An empty result returned by Elasticsearch when no match is found."
  {:took 0, :timed_out false, :_shards {:total 0, :successful 0, :skipped 0, :failed 0}, :hits {:total {:value 0}, :max_score nil, :hits ()}})

(defn- granule-no-coll-match?
  "Returns true if this is a granule query with a collection identifier
   but no matching collection IDs, meaning we should skip querying ES."
  [{:keys [query-concept-type has-coll-identifier query-collection-ids]}]
  (and (= query-concept-type :granule)
       has-coll-identifier
       (empty? query-collection-ids)))

(defn- run-elasticsearch-query
  "Runs the Elasticsearch query pipeline: preprocess, apply ACLs (if needed),
   reduce, and execute."
  [context processed-query]
  (let [processed-query (pre-process-query-result-features context processed-query)]
    (->> processed-query
         (#(if (or (tc/echo-system-token? context) (:skip-acls? %))
             %
             (add-acl-conditions-to-query context %)))
         (c2s/reduce-query context)
         (idx/execute-query context))))


(defmethod execute-query :elasticsearch
  [context query]
  (let [[context processed-query] (concept-type-specific-query-processing context query)
        elastic-results (if (granule-no-coll-match? context)
                          (do
                            (debug "No collection matched by collection identifying parameters in granule search, skip querying ES.")
                            empty-es-results)
                          (run-elasticsearch-query context processed-query))
        query-results (rc/elastic-results->query-results context processed-query elastic-results)]
    (post-process-query-result-features context query elastic-results query-results)))
