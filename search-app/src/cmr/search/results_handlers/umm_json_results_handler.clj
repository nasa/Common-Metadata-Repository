(ns cmr.search.results-handlers.umm-json-results-handler
  "Handles the umm-json results format and related functions"
  (:require [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
            [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
            [cmr.common-app.services.search :as qs]
            [cmr.common-app.services.search.results-model :as results]
            [cmr.search.data.metadata-retrieval.metadata-cache :as metadata-cache]
            [cheshire.core :as json]
            [clojure.string :as str]
            [cmr.common.util :as util]
            [cmr.common.mime-types :as mt]))

(def meta-fields
  "Defines the fields in elastic search we retrieve to populate the meta fields in the response."
  ["concept-id"
   "revision-id"
   "native-id"
   "user-id"
   "provider-id"
   "metadata-format"
   "revision-date"
   "deleted"])

(defn- elastic-result->meta
  "Takes an elasticsearch result and returns a map of the meta fields for the response."
  [elastic-result]
  (let [{[concept-id] :concept-id
         [revision-id] :revision-id
         [native-id] :native-id
         [user-id] :user-id
         [provider-id] :provider-id
         [metadata-format] :metadata-format
         [revision-date] :revision-date
         [deleted] :deleted} (:fields elastic-result)
        revision-date (when revision-date (str/replace (str revision-date) #"\+0000" "Z"))]
    (util/remove-nil-keys
     {:concept-type :collection
      :concept-id concept-id
      :revision-id revision-id
      :native-id native-id
      :user-id user-id
      :provider-id provider-id
      :format (mt/format->mime-type (keyword metadata-format))
      :revision-date revision-date
      :deleted deleted})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UMM JSON

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :umm-json-results]
  [concept-type query]
  meta-fields)

(defmethod elastic-results/elastic-result->query-result-item [:collection :umm-json-results]
  [context query elastic-result]
  (let [{[entry-title] :entry-title
         [entry-id] :entry-id
         [short-name] :short-name
         [version-id] :version-id} (:fields elastic-result)]
    {:meta (elastic-result->meta elastic-result)
     :umm {:entry-title entry-title
           :entry-id entry-id
           :short-name short-name
           :version-id version-id}}))

(defn- elastic-result->tuple
  "Returns a tuple of concept id and revision id from the elastic result"
  [elastic-result]
  [(get-in elastic-result [:fields :concept-id 0])
   (elastic-results/get-revision-id-from-elastic-result :collection elastic-result)])

(defmethod elastic-results/elastic-results->query-results [:collection :umm-json-results]
  [context query elastic-results]
  (let [{:keys [result-format]} query
        hits (get-in elastic-results [:hits :total])
        elastic-matches (get-in elastic-results [:hits :hits])
        ;; Get concept metadata in specified UMM format and version
        tuples (mapv elastic-result->tuple elastic-matches)
        concepts (metadata-cache/get-formatted-concept-revisions
                  context :collection tuples (assoc result-format :format :umm-json))
        ;; Convert concepts into items with parsed umm.
        items (mapv (fn [elastic-result concept]
                      (if (:deleted concept)
                        {:meta (elastic-result->meta elastic-result)}
                        {:meta (elastic-result->meta elastic-result)
                         :umm (json/decode (:metadata concept))}))
                    elastic-matches
                    concepts)]
    (results/map->Results {:hits hits :items items :result-format result-format})))



(defmethod qs/search-results->response [:collection :umm-json-results]
  [context query results]
  (json/generate-string (select-keys results [:hits :took :items])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Legacy UMM JSON

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :legacy-umm-json]
  [concept-type query]
  (concat
   meta-fields
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
    {:meta (elastic-result->meta elastic-result)
     :umm {:entry-title entry-title
           :entry-id entry-id
           :short-name short-name
           :version-id version-id}}))

(defmethod qs/search-results->response [:collection :legacy-umm-json]
  [context query results]
  (json/generate-string (select-keys results [:hits :took :items])))

