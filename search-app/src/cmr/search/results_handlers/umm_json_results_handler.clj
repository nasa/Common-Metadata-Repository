(ns cmr.search.results-handlers.umm-json-results-handler
  "Handles the umm-json results format and related functions"
  (:require [cmr.search.data.elastic-results-to-query-results :as elastic-results]
            [cmr.search.data.elastic-search-index :as elastic-search-index]
            [cmr.search.services.query-service :as qs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [cmr.common.util :as util]
            [cmr.common.mime-types :as mt]))

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :umm-json]
  [concept-type query]
  ["concept-id"
   "native-id"
   "user-id"
   "provider-id"
   "entry-title"
   "entry-id"
   "short-name"
   "version-id"
   "revision-date2"
   "deleted"
   "metadata-format"])

(defmethod elastic-results/elastic-result->query-result-item :umm-json
  [context query elastic-result]
  (let [{[concept-id] :concept-id
         [native-id] :native-id
         [user-id] :user-id
         [provider-id] :provider-id
         [entry-title] :entry-title
         [entry-id] :entry-id
         [short-name] :short-name
         [version-id] :version-id
         [revision-date] :revision-date2
         [deleted] :deleted
         [metadata-format] :metadata-format} (:fields elastic-result)
        revision-date (when revision-date (str/replace (str revision-date) #"\+0000" "Z"))
        revision-id (:_version elastic-result)]
    {:meta (util/remove-nil-keys
             {:concept-type :collection
              :concept-id concept-id
              :revision-id revision-id
              :native-id native-id
              :user-id user-id
              :provider-id provider-id
              :format (mt/format->mime-type (keyword metadata-format))
              :revision-date revision-date
              :deleted deleted})
     :umm {:entry-title entry-title
           :entry-id entry-id
           :short-name short-name
           :version-id version-id}}))

(defmethod qs/search-results->response :umm-json
  [context query results]
  (json/generate-string (select-keys results [:hits :took :items])))

