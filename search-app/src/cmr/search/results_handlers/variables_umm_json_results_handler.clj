(ns cmr.search.results-handlers.variables-umm-json-results-handler
  "Handles Variable umm-json results format and related functions"
  (:require
   [cheshire.core :as json]
   [cmr.common-app.services.search :as qs]
   [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
   [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
   [cmr.common-app.services.search.results-model :as results]
   [cmr.search.data.metadata-retrieval.metadata-cache :as metadata-cache]
   [cmr.search.results-handlers.umm-json-results-helper :as results-helper]))

(defn- variable-elastic-result->meta
  "Returns a map of the meta fields for the given variable elastic result."
  [elastic-result]
  (results-helper/elastic-result->meta :variable elastic-result))

(defmethod elastic-search-index/concept-type+result-format->fields [:variable :umm-json-results]
  [concept-type query]
  results-helper/meta-fields)

(defmethod elastic-results/elastic-result->query-result-item [:variable :umm-json-results]
  [context query elastic-result]
  (let [{[entry-title] :entry-title
         [entry-id] :entry-id
         [short-name] :short-name
         [version-id] :version-id} (:fields elastic-result)]
    {:meta (variable-elastic-result->meta elastic-result)
     :umm {:entry-title entry-title
           :entry-id entry-id
           :short-name short-name
           :version-id version-id}}))

(defmethod elastic-results/elastic-results->query-results [:variable :umm-json-results]
  [context query elastic-results]
  (let [{:keys [result-format]} query
        hits (get-in elastic-results [:hits :total])
        elastic-matches (get-in elastic-results [:hits :hits])
        ;; Get concept metadata in specified UMM format and version
        tuples (mapv (partial results-helper/elastic-result->tuple :variable) elastic-matches)
        concepts (metadata-cache/get-formatted-concept-revisions
                  context :variable tuples (assoc result-format :format :umm-json))
        ;; Convert concepts into items with parsed umm.
        items (mapv (fn [elastic-result concept]
                      (if (:deleted concept)
                        {:meta (variable-elastic-result->meta elastic-result)}
                        {:meta (variable-elastic-result->meta elastic-result)
                         :umm (json/decode (:metadata concept))}))
                    elastic-matches
                    concepts)]
    (results/map->Results {:hits hits :items items :result-format result-format})))

(defmethod qs/search-results->response [:variable :umm-json-results]
  [context query results]
  (json/generate-string (select-keys results [:hits :took :items])))
