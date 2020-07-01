(ns cmr.search.results-handlers.granules-umm-json-results-handler
  "Handles granule umm-json results format and related functions"
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.common-app.services.search :as qs]
   [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
   [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.search.results-handlers.umm-json-results-helper :as results-helper]))

(def granule-meta-fields
  "Defines the fields in elastic search we retrieve to populate the meta fields
  in granule UMM JSON response."
  ["concept-id"
   "revision-id"
   "native-id"
   "provider-id"
   "metadata-format"
   "revision-date"])

(defn granule-elastic-result->meta
  "Takes an elasticsearch result and returns a map of the meta fields in granule UMM JSON response."
  [elastic-result]
  (let [{concept-id :concept-id
         revision-id :revision-id
         native-id :native-id
         provider-id :provider-id
         metadata-format :metadata-format
         revision-date :revision-date} (:_source elastic-result)
        revision-date (when revision-date (string/replace (str revision-date) #"\+0000" "Z"))]
    (util/remove-nil-keys
     {:concept-type :granule
      :concept-id concept-id
      :revision-id revision-id
      :native-id native-id
      :provider-id provider-id
      :format (mt/format->mime-type (keyword metadata-format))
      :revision-date revision-date})))

(defmethod elastic-search-index/concept-type+result-format->fields [:granule :umm-json-results]
  [concept-type query]
  granule-meta-fields)

(defmethod results-helper/elastic-result+metadata->umm-json-item :granule
  [concept-type elastic-result metadata]
  {:meta (granule-elastic-result->meta elastic-result)
   :umm (json/decode metadata)})

(defmethod elastic-results/elastic-results->query-results [:granule :umm-json-results]
  [context query elastic-results]
  (results-helper/query-elastic-results->query-results context :granule query elastic-results))

(defmethod qs/search-results->response [:granule :umm-json-results]
  [context query results]
  (json/generate-string (select-keys results [:hits :took :items])))
