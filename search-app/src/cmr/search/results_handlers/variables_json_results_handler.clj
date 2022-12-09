(ns cmr.search.results-handlers.variables-json-results-handler
  "Handles extracting elasticsearch variable results and converting them into a JSON search response."
  (:require
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [cmr.common-app.services.search :as qs]
   [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
   [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
   [cmr.common.util :as util]
   [cmr.search.results-handlers.results-handler-util :as rs-util]))

(defmethod elastic-search-index/concept-type+result-format->fields [:variable :json]
  [concept-type query]
  ["concept-id" "revision-id" "deleted" "provider-id" "native-id" "variable-name" "measurement" "associations-gzip-b64"])

(defmethod elastic-results/elastic-result->query-result-item [:variable :json]
  [context query elastic-result]
  (let [{{variable-name :variable-name
          measurement :measurement
          deleted :deleted
          provider-id :provider-id
          native-id :native-id
          concept-id :concept-id
          associations-gzip-b64 :associations-gzip-b64} :_source} elastic-result
          associations (some-> associations-gzip-b64
                               util/gzip-base64->string
                               edn/read-string)
        revision-id (elastic-results/get-revision-id-from-elastic-result :variable elastic-result)
        result-item (util/remove-nil-keys
                     {:concept_id concept-id
                      :revision_id revision-id
                      :provider_id provider-id
                      :native_id native-id
                      :name variable-name
                      :long_name measurement
                      :associations (rs-util/build-association-concept-id-list associations :variable)
                      :association_details (rs-util/build-association-details (rs-util/replace-snake-keys associations) :variable)})]
    (if deleted
      (assoc result-item :deleted deleted)
      result-item)))

(defmethod qs/search-results->response [:variable :json]
  [context query results]
  (json/generate-string (select-keys results [:hits :took :items])))
