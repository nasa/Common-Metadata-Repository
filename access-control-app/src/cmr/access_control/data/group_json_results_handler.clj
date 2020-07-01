(ns cmr.access-control.data.group-json-results-handler
  "Handles extracting elasticsearch group results and converting them into a JSON search response."
  (:require
   [cheshire.core :as json]
   [cmr.common-app.services.search :as qs]
   [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
   [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
   [cmr.common.util :as util]))

(def base-fields
  "The base set of fields to select from Elasticsearch"
  ["concept-id" "revision-id" "name" "provider-id" "description" "legacy-guid" "member-count"])

(def fields-with-members
  "The fields to select from Elasticsearch if members should be included"
  (conj base-fields "members"))

(defn- include-members?
  "Returns true if the query indicates members should be returned in the response."
  [query]
  (some #{:include-members} (:result-features query)))

(defmethod elastic-search-index/concept-type+result-format->fields [:access-group :json]
  [concept-type query]
  (if (include-members? query)
    fields-with-members
    base-fields))

(defmethod elastic-results/elastic-result->query-result-item [:access-group :json]
  [context query elastic-result]
  (let [field-values (:_source elastic-result)
        item (dissoc field-values :members)]
    (if (include-members? query)
      (assoc item :members (:members field-values))
      item)))

(defmethod qs/search-results->response [:access-group :json]
  [context query results]
  (let [results (select-keys results [:hits :took :items])
        converted-items (util/map-keys->snake_case results)]
   (json/generate-string converted-items)))
