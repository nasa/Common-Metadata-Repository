(ns cmr.search.results-handlers.umm-json-results-handler
  "Handles the umm-json results format and related functions"
  (:require [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
            [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
            [cmr.common-app.services.search :as qs]
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
   "revision-date"
   "revision-id"
   "deleted"
   "metadata-format"])

(defmethod elastic-results/elastic-result->query-result-item [:collection :umm-json]
  [context query elastic-result]
  (let [{[concept-id] :concept-id
         [native-id] :native-id
         [user-id] :user-id
         [provider-id] :provider-id
         [entry-title] :entry-title
         [entry-id] :entry-id
         [short-name] :short-name
         [version-id] :version-id
         [revision-date] :revision-date
         [revision-id] :revision-id
         [deleted] :deleted
         [metadata-format] :metadata-format} (:fields elastic-result)
        revision-date (when revision-date (str/replace (str revision-date) #"\+0000" "Z"))]
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

(defmethod qs/search-results->response [:collection :umm-json]
  [context query results]
  (json/generate-string (select-keys results [:hits :took :items])))

