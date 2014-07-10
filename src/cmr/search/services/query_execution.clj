(ns cmr.search.services.query-execution
  (:require [cmr.search.models.query :as qm]
            [cmr.search.data.elastic-search-index :as idx]
            [cmr.transmit.transformer :as t]
            [cmr.search.models.results :as results]
            [cmr.search.data.elastic-results-to-query-results :as rc]
            [cmr.common.log :refer (debug info warn error)])
  (:import cmr.search.models.query.StringsCondition
           cmr.search.models.query.StringCondition))

(def non-transformer-supported-formats
  "Formats that the transformer does not support because they're implemented in search. Assumed
  that the transformer will support any format not listed here."
  #{:csv :json :xml})

(def transformer-supported-format?
  "Returns true if the format is supported by the transformer."
  (complement non-transformer-supported-formats))

(defn- direct-transformer-query?
  "Returns true if the query should be executed directly against the transformer and bypass elastic."
  [{:keys [condition concept-type result-format page-num page-size sort-keys] :as query}]
  (and (transformer-supported-format? result-format)
       (#{StringCondition StringsCondition} (type condition))
       (= :concept-id (:field condition))
       (= page-num 1)
       (>= page-size (count (:values condition)))

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

(defmulti execute-query
  "Executes the query using the most appropriate mechanism"
  (fn [context query]
    (query->execution-strategy query)))

(defmethod execute-query :direct-transformer
  [context query]
  (let [{:keys [result-format pretty?]} query
        concept-ids (query->concept-ids query)
        tresults (t/get-latest-formatted-concepts context concept-ids result-format)
        items (map #(select-keys % [:concept-id :revision-id :collection-concept-id :metadata]) tresults)]
    (results/map->Results {:hits (count items)
                           :items items})))

(defmethod execute-query :elastic
  [context query]
  (->> query
       (idx/execute-query context)
       (rc/elastic-results->query-results context query)))


