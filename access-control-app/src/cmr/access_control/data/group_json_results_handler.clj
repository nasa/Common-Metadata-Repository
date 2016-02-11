(ns cmr.access-control.data.group-json-results-handler
  "Handles extracting elasticsearch group results and converting them into a JSON search response."
  (:require [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
            [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
            [cmr.common-app.services.search :as qs]
            [cmr.common.util :as util]
            [cheshire.core :as json]))

(defmethod elastic-search-index/concept-type+result-format->fields [:access-group :json]
  [concept-type query]
  ["concept-id" "revision-id" "name" "provider-id" "description" "legacy-guid" "member-count"])

(defmethod elastic-results/elastic-result->query-result-item [:access-group :json]
  [context query elastic-result]
  (->> elastic-result
      :fields
      (util/map-values first)))

(defmethod qs/search-results->response [:access-group :json]
  [context query results]
  (json/generate-string (select-keys results [:hits :took :items])))