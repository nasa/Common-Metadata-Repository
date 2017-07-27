(ns cmr.search.results-handlers.variables-json-results-handler
  "Handles extracting elasticsearch variable results and converting them into a JSON search response."
  (:require
   [cheshire.core :as json]
   [cmr.common-app.services.search :as qs]
   [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
   [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]))

(defmethod elastic-search-index/concept-type+result-format->fields [:variable :json]
  [concept-type query]
  ["concept-id" "provider-id" "native-id" "variable-name" "measurement"])

(defmethod elastic-results/elastic-result->query-result-item [:variable :json]
  [context query elastic-result]
  (let [{{[variable-name] :variable-name
          [measurement] :measurement
          [provider-id] :provider-id
          [native-id] :native-id
          [concept-id] :concept-id} :fields} elastic-result
        revision-id (elastic-results/get-revision-id-from-elastic-result :variable elastic-result)]
    {:concept-id concept-id
     :revision-id revision-id
     :provider-id provider-id
     :native-id native-id
     :variable-name variable-name
     :measurement measurement}))

(defmethod qs/search-results->response [:variable :json]
  [context query results]
  (json/generate-string (select-keys results [:hits :took :items])))
