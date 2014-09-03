(ns cmr.search.services.query-execution
  (:require [cmr.search.models.query :as qm]
            [cmr.search.data.elastic-search-index :as idx]
            [cmr.search.services.transformer :as t]
            [cmr.search.models.results :as results]
            [cmr.search.data.elastic-results-to-query-results :as rc]
            [cmr.search.data.complex-to-simple :as c2s]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.search.services.query-walkers.collection-query-resolver :as r]
            [cmr.search.services.acl-service :as acl-service])
  (:import cmr.search.models.query.StringsCondition
           cmr.search.models.query.StringCondition))

(def non-transformer-supported-formats
  "Formats that the transformer does not support because they're implemented in search. Assumed
  that the transformer will support any format not listed here."
  #{:csv :json :xml :atom :atom-links})

(def transformer-supported-format?
  "Returns true if the format is supported by the transformer."
  (complement non-transformer-supported-formats))

(defn- direct-transformer-query?
  "Returns true if the query should be executed directly against the transformer and bypass elastic."
  [{:keys [condition concept-type result-format page-num page-size sort-keys
           result-features] :as query}]
  (and (transformer-supported-format? result-format)
       (#{StringCondition StringsCondition} (type condition))
       (= :concept-id (:field condition))
       (= page-num 1)
       (>= page-size (count (:values condition)))
       ;; Facets requires elastic search
       (not-any? #(= % :facets) result-features)

       ;; sorting has been left at the default level
       ;; Note that we don't actually sort items by the default sort keys
       ;; See issue CMR-607
       (= (concept-type qm/default-sort-keys) sort-keys)))

(defn- query->execution-strategy
  "Determines the execution strategy to use for the given query."
  [query]
  (if (direct-transformer-query? query)
    :direct-transformer
    :elastic))

(defmulti query->concept-ids
  "Extract concept ids from a concept-id only query"
  (fn [query]
    (class (:condition query))))

(defmethod query->concept-ids StringCondition
  [query]
  [(get-in query [:condition :value])])

(defmethod query->concept-ids StringsCondition
  [query]
  (get-in query [:condition :values]))

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

;; TODO enforce granule ACLs on direct transformer queries
(defmethod execute-query :direct-transformer
  [context query]
  (let [{:keys [result-format pretty?]} query
        concept-ids (query->concept-ids query)
        tresults (t/get-latest-formatted-concepts context concept-ids result-format)
        items (map #(select-keys % [:concept-id :revision-id :collection-concept-id :metadata]) tresults)
        results (results/map->Results {:hits (count items) :items items :result-format result-format})]
    (post-process-query-result-features context query nil results)))

(defmethod execute-query :elastic
  [context query]
  (let [elastic-results (->> query
                             (pre-process-query-result-features context)
                             c2s/reduce-query
                             (r/resolve-collection-queries context)
                             (acl-service/add-acl-conditions-to-query context)
                             (idx/execute-query context))
        query-results (rc/elastic-results->query-results context query elastic-results)]
    (post-process-query-result-features context query elastic-results query-results)))



