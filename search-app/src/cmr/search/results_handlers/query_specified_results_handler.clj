(ns cmr.search.results-handlers.query-specified-results-handler
  "Allows internal use of querying to retrieve specific data from elastic search. The query can
  specify fields to retrieve and extract using :fields in the query. A function for processing
  elastic items can be also be specified in the :result-features of the query."
  (:require [cmr.search.data.elastic-results-to-query-results :as elastic-results]
            [cmr.search.data.elastic-search-index :as elastic-search-index]))

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

(defn- elastic-result->query-result-item
  [context query elastic-result]
  (let [processor (get-elastic-result-item-processor query)]
    (processor context query elastic-result)))

(defmethod elastic-results/elastic-results->query-results [:granule :query-specified]
  [context query elastic-results]
  (assoc (elastic-results/default-elastic-results->query-results context query elastic-results)
         :aggregations (:aggregations elastic-results)))

(doseq [concept-type [:collection :granule :tag]]

  (defmethod elastic-search-index/concept-type+result-format->fields [concept-type :query-specified]
    [concept-type query]
    (map name (:fields query)))

  (defmethod elastic-results/elastic-result->query-result-item [concept-type :query-specified]
    [context query elastic-result]
    (elastic-result->query-result-item context query elastic-result)))
