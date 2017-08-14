(ns cmr.search.results-handlers.umm-json-results-handler
  "Handles Collection umm-json results format and related functions"
  (:require
   [cheshire.core :as json]
   [cmr.common-app.services.search :as qs]
   [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
   [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
   [cmr.search.results-handlers.umm-json-results-helper :as results-helper]))

(defn- collection-elastic-result->meta
  "Returns a map of the meta fields for the given collection elastic result."
  [elastic-result]
  (results-helper/elastic-result->meta :collection elastic-result))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Collection UMM JSON

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :umm-json-results]
  [concept-type query]
  results-helper/meta-fields)

(defmethod elastic-results/elastic-result->query-result-item [:collection :umm-json-results]
  [context query elastic-result]
  (let [{[entry-title] :entry-title
         [entry-id] :entry-id
         [short-name] :short-name
         [version-id] :version-id} (:fields elastic-result)]
    {:meta (collection-elastic-result->meta elastic-result)
     :umm {:entry-title entry-title
           :entry-id entry-id
           :short-name short-name
           :version-id version-id}}))

(defmethod results-helper/elastic-result+metadata->umm-json-item :collection
  [concept-type elastic-result metadata]
  {:meta (collection-elastic-result->meta elastic-result)
   :umm (json/decode metadata)})

(defmethod elastic-results/elastic-results->query-results [:collection :umm-json-results]
  [context query elastic-results]
  (results-helper/query-elastic-results->query-results context :collection query elastic-results))

(defmethod qs/search-results->response [:collection :umm-json-results]
  [context query results]
  (json/generate-string (select-keys results [:hits :took :items])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Collection Legacy UMM JSON

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :legacy-umm-json]
  [concept-type query]
  (concat
   results-helper/meta-fields
   ["entry-title"
    "entry-id"
    "short-name"
    "version-id"]))

(defmethod elastic-results/elastic-result->query-result-item [:collection :legacy-umm-json]
  [context query elastic-result]
  (let [{[entry-title] :entry-title
         [entry-id] :entry-id
         [short-name] :short-name
         [version-id] :version-id} (:fields elastic-result)]
    {:meta (collection-elastic-result->meta elastic-result)
     :umm {:entry-title entry-title
           :entry-id entry-id
           :short-name short-name
           :version-id version-id}}))

(defmethod qs/search-results->response [:collection :legacy-umm-json]
  [context query results]
  (json/generate-string (select-keys results [:hits :took :items])))
