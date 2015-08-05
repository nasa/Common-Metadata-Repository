(ns cmr.search.results-handlers.concept-map-handler
  "Handles the concept map results format and related functions"
  (:require [cmr.search.data.elastic-results-to-query-results :as elastic-results]
            [cmr.search.data.elastic-search-index :as elastic-search-index]
            [cmr.search.services.query-service :as qs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [cmr.common.util :as util]
            [cmr.common.mime-types :as mt]))

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :concept-map]
  [concept-type query]
  ["concept-id"
   "native-id"
   "provider-id"
   "entry-title"
   "entry-id"
   "short-name"
   "version-id"
   "revision-date"
   "deleted"
   "metadata-format"])

(defmethod elastic-results/elastic-result->query-result-item :concept-map
  [context query elastic-result]
  (let [{[concept-id] :concept-id
         [native-id] :native-id
         [provider-id] :provider-id
         [entry-title] :entry-title
         [entry-id] :entry-id
         [short-name] :short-name
         [version-id] :version-id
         [revision-date] :revision-date
         [deleted] :deleted
         [metadata-format] :metadata-format} (:fields elastic-result)
        revision-date (when revision-date (str/replace (str revision-date) #"\+0000" "Z"))
        revision-id (:_version elastic-result)]
    {:concept-type :collection
     :concept-id concept-id
     :revision-id revision-id
     :native-id native-id
     :provider-id provider-id
     :entry-title entry-title
     :entry-id entry-id
     :short-name short-name
     :version-id version-id
     :revision-date revision-date
     :deleted deleted
     :format (mt/format->mime-type (keyword metadata-format))}))

(defmethod qs/search-results->response :concept-map
  [context query results]
  (json/generate-string (:items results)))

