(ns cmr.common-app.services.search.elastic-results-to-query-results
  "Contains functions to convert elasticsearch results to query results."
  (:require
   [cmr.common-app.services.search.query-model :as common-qm]
   [cmr.common-app.services.search.results-model :as results]))

(defn get-hits
  "Returns hits from the given elastic-results"
  [elastic-results]
  (get-in elastic-results [:hits :total :value]))

(defn get-timedout
  "Returns timedout from the given elastic-results"
  [elastic-results]
  (:timed_out elastic-results))

(defn get-scroll-id
  "Returns scroll-id from the given elastic-results"
  [elastic-results]
  (:_scroll_id elastic-results))

(defn get-search-after
  "Returns search-after from the given elastic-results"
  [elastic-results]
  (-> elastic-results
      (get-in [:hits :hits])
      last
      :sort))

(defn get-elastic-matches
  "Returns matches from the given elastic-results"
  [elastic-results]
  (get-in elastic-results [:hits :hits]))

(defmulti elastic-result->query-result-item
  "Converts the Elasticsearch result into the result expected from execute-query for the given format."
  (fn [context query elastic-result]
    (let [result-format (common-qm/base-result-format query)]
      (if (= :query-specified result-format)
        ;; The same result reader is used for every concept type when query specified
        result-format
        [(:concept-type query) result-format]))))

(defn- default-query-specified-elastic-result-item-processor
  "The default function that will be used to process an elastic result into a result for the caller."
  [context query elastic-result]
  (let [{concept-id :_id
         field-values :_source} elastic-result]
    (reduce #(assoc %1 %2 (-> field-values %2))
            {:concept-id concept-id}
            (:result-fields query))))

(defmethod elastic-result->query-result-item :query-specified
  [context query elastic-result]
  (let [processor (get-in query [:result-features :query-specified :result-processor]
                          default-query-specified-elastic-result-item-processor)]
    (processor context query elastic-result)))

(defmulti elastic-results->query-results
  "Converts elastic search results to query results"
  (fn [context query elastic-results]
    [(:concept-type query) (common-qm/base-result-format query)]))

(defn default-elastic-results->query-results
  "Default function for converting elastic-results to query-results"
  [context query elastic-results]
  (let [hits (get-hits elastic-results)
        timed-out (get-timedout elastic-results)
        scroll-id (get-scroll-id elastic-results)
        search-after (get-search-after elastic-results)
        elastic-matches (get-elastic-matches elastic-results)
        items (mapv #(elastic-result->query-result-item context query %) elastic-matches)]
    (results/map->Results
     {:aggregations (:aggregations elastic-results)
      :hits hits
      :timed-out timed-out
      :items items
      :result-format (:result-format query)
      :scroll-id scroll-id
      :search-after search-after})))

(defmethod elastic-results->query-results :default
  [context query elastic-results]
  (default-elastic-results->query-results context query elastic-results))

(defmulti get-revision-id-from-elastic-result
  "Returns the revision-id from elastic result for the given concept-type"
  (fn [concept-type elastic-result]
    concept-type))

(defmethod get-revision-id-from-elastic-result :default
  [concept-type elastic-result]
  (:_version elastic-result))
