(ns cmr.search.services.query-execution
  (:require [cmr.search.models.query :as qm]
            [cmr.search.data.elastic-search-index :as idx]
            [cmr.transmit.transformer :as t]
            [cmr.search.services.search-results :as sr]
            [cmr.search.models.results :as results]
            [cmr.search.data.elastic-results-to-query-results :as rc])
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
  [{:keys [condition result-format]}]
  (and (transformer-supported-format? result-format)
       (#{StringCondition StringsCondition} (type condition))

       ;; TODO Add the following conditions
       ;; and test it too
       ;; page_num must be 1
       ;; page_size must be >= number of ids
       ;; sort must be unset

       ;; TODO file issue that sorting in this case is not the normal default.
       ;; it's sorted by the order the items are listed in the query

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
  [(get-in query [:condition :value])])

(defmethod query->concept-ids StringsCondition
  [query]
  (get-in query [:condition :values]))

(defmulti execute-query
  "Executes the query using the most appropriate mechanism"
  (fn [context query]
    (query->execution-type query)))

(defmethod execute-query :direct-transformer
  [context query]
  (let [start (System/currentTimeMillis)
        {:keys [result-format pretty?]} query
        concept-ids (query->concept-ids query)
        tresults (t/get-latest-formatted-concepts context concept-ids result-format)
        items (map #(select-keys % [:concept-id :revision-id :collection-concept-id :metadata]) tresults)
        ;; TODO add timing macro
        took (- (System/currentTimeMillis) start)]
    (results/map->Results {:hits (count items)
                           :references items
                           :took took})))

(defmethod execute-query :elastic
  [context query]
  (let [start (System/currentTimeMillis)
        {:keys [result-format concept-type]} query
        elastic-results (idx/execute-query context query)
        results (rc/elastic-results->query-results context concept-type elastic-results result-format)
        ;; TODO use timing macro
        took (- (System/currentTimeMillis) start)]
    (assoc results :took took)))