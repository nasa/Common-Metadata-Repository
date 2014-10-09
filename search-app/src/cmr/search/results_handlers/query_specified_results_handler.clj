(ns cmr.search.results-handlers.query-specified-results-handler
  "Allows internal use of querying to retrieve specific data from elastic search. The query can
  specify fields to retrieve and extract using :fields in the query."
  (:require [cmr.search.data.elastic-results-to-query-results :as elastic-results]
            [cmr.search.data.elastic-search-index :as elastic-search-index]))

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :query-specified]
  [concept-type query]
  (map name (:fields query)))

(defmethod elastic-search-index/concept-type+result-format->fields [:granule :query-specified]
  [concept-type query]
  (map name (:fields query)))

(defmethod elastic-results/elastic-result->query-result-item :query-specified
  [context query elastic-result]
  (let [query-fields (:fields query)
        {concept-id :_id
         field-values :fields} elastic-result]
    (reduce #(assoc %1 %2 (-> field-values %2 first))
            {:concept-id concept-id}
            query-fields)))
