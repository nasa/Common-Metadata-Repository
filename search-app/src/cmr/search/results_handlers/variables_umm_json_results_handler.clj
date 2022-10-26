(ns cmr.search.results-handlers.variables-umm-json-results-handler
  "Handles Variable umm-json results format and related functions"
  (:require
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [cmr.common-app.services.search :as qs]
   [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
   [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
   [cmr.common.util :as util]
   [cmr.search.results-handlers.umm-json-results-helper :as results-helper]))

(defmethod elastic-search-index/concept-type+result-format->fields [:variable :umm-json-results]
  [concept-type query]
  (concat
   results-helper/meta-fields
   ["associations-gzip-b64"]))

(defn- elastic-result->associated-concepts
  "Returns the associated concepts of the elastic result"
  [elastic-result]
  (let [associations-gzip-b64 (get-in elastic-result [:_source :associations-gzip-b64])]
    (when associations-gzip-b64
      (edn/read-string
       (util/gzip-base64->string associations-gzip-b64)))))

(defmethod results-helper/elastic-result+metadata->umm-json-item :variable
  [concept-type elastic-result metadata]
  (let [base-result-item {:meta (results-helper/elastic-result->meta :variable elastic-result)
                          :umm (json/decode metadata)}
        associated-concepts (elastic-result->associated-concepts elastic-result)]
    (if associated-concepts
      (assoc base-result-item :associations associated-concepts)
      base-result-item)))

(defmethod elastic-results/elastic-results->query-results [:variable :umm-json-results]
  [context query elastic-results]
  (results-helper/query-elastic-results->query-results context :variable query elastic-results))

(defmethod qs/search-results->response [:variable :umm-json-results]
  [context query results]
  (json/generate-string (select-keys results [:hits :took :items])))
