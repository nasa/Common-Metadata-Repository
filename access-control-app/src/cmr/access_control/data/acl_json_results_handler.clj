(ns cmr.access-control.data.acl-json-results-handler
  "Handles extracting elasticsearch acl results and converting them into a JSON search response."
  (:require [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
            [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
            [cmr.common-app.services.search :as qs]
            [cmr.common.util :as util]
            [clojure.set :as set]
            [cheshire.core :as json]))

(defmethod elastic-search-index/concept-type+result-format->fields [:acl :json]
  [concept-type query]
  ["concept-id" "revision-id" "display-name" "identity-type"])

(defmethod elastic-results/elastic-result->query-result-item [:acl :json]
  [context query elastic-result]
  (set/rename-keys
   (->> elastic-result
        :fields
        (util/map-values first))
   {:display-name :name}))

(defmethod qs/search-results->response [:acl :json]
  [context query results]
  (let [results (select-keys results [:hits :took :items])
        converted-items (util/map-keys->snake_case results)]
   (json/generate-string converted-items)))
