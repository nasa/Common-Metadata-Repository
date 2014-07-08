(ns cmr.search.services.query-execution
  (:require [cmr.search.models.query :as qm]
            [cmr.search.data.elastic-search-index :as idx]
            [cmr.transmit.transformer :as t])
  (:import cmr.search.models.query.StringsCondition
           cmr.search.models.query.StringCondition))

;; TODO James, the search results should be moved from API to here.
;; this namespace will be responsible for returning the data in the format requested by the user.

(def non-transformer-supported-formats
  "Formats that the transformer does not support because they're implemented in search. Assumed
  that the transformer will support any format not listed here."
  #{:csv :json})

(def transformer-supported-format?
  "Returns true if the format is supported by the transformer."
  (complement non-transformer-supported-formats))

(defn- direct-transformer-query?
  "Returns true if the query should be executed directly against the transformer and bypass elastic."
  [{:keys [condition result-format]}]
  (and (transformer-supported-format? result-format)
       (or (= StringCondition (type condition)) (= StringsCondition (type condition)))
       (= :concept-id (:field condition))))

(defn- query->execution-type
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
  (vector (get-in query [:condition :value])))

(defmethod query->concept-ids StringsCondition
  [query]
  (get-in query [:condition :values]))

(defmulti execute-query
  "Executes the query using the most appropriate mechanism"
  (fn [context query]
    (query->execution-type query)))

(defmethod execute-query :direct-transformer
  [context query]
  (let [concept-ids (query->concept-ids query)
        result-format (:result-format query)
        tresults (t/get-latest-formatted-concepts context concept-ids result-format)
        metadata (map :metadata tresults)
        refs (map #(select-keys % [:concept-id :revision-id :collection-concept-id]) tresults)]
    {:metadatas metadata
     :references refs
     :hits (count refs)}))

(defmethod execute-query :elastic
  [context query]
  (idx/execute-query context query))