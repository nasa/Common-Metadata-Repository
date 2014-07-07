(ns cmr.search.services.query-execution
  (:require [cmr.search.models.query :as qm]
            [cmr.search.data.elastic-search-index :as idx])
  (:import cmr.search.models.query.StringsCondition))

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
       (= StringsCondition (type condition))
       (= :concept-id (:field condition))))

(defn- query->execution-type
  [query]
  (if (direct-transformer-query? query)
    ;:direct-transformer - TODO put this back when done with initial testing
    :elastic
    :elastic))

(defmulti execute-query
  "Executes the query using the most appropriate mechanism"
  (fn [context query]
    (query->execution-type query)))

(defmethod execute-query :direct-transformer
  [context query]
  )

(defmethod execute-query :elastic
  [context query]
  (idx/execute-query context query)
  )