(ns cmr.search.results-handlers.query-specified-results-handler
  "Allows internal use of querying to retrieve specific data from elastic search. The query can
  specify fields to retrieve and extract using :fields in the query. A function for processing
  elastic items can be also be specified in the :result-features of the query."
  (:require [cmr.search.data.elastic-results-to-query-results :as elastic-results]
            [cmr.search.data.elastic-search-index :as elastic-search-index]))

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :query-specified]
  [concept-type query]
  (map name (:fields query)))

(defmethod elastic-search-index/concept-type+result-format->fields [:granule :query-specified]
  [concept-type query]
  (map name (:fields query)))

(defn- elastic-result-item-processor
  "The default function that will be used to process an elastic result into a result for the caller."
  [context query elastic-result]
  (let [{concept-id :_id
         field-values :fields} elastic-result]
    (reduce #(assoc %1 %2 (-> field-values %2 first))
            {:concept-id concept-id}
            (:fields query))))

(defn- get-elastic-result-item-processor
  "Gets the query specified result processor or the default."
  [query]
  (get-in query [:result-features :query-specified :result-processor] elastic-result-item-processor))

(defmethod elastic-results/elastic-result->query-result-item :query-specified
  [context query elastic-result]
  (let [processor (get-elastic-result-item-processor query)]
    (processor context query elastic-result)))
