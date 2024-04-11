(ns cmr.access-control.data.acl-json-results-handler
  "Handles extracting elasticsearch acl results and converting them into a JSON search response."
  (:require
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [clojure.set :as set]
   [cmr.common-app.services.search :as qs]
   [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
   [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
   [cmr.common.util :as util]
   [cmr.transmit.config :as tconfig]))

(defn- reference-root
  "Returns the url root for reference location"
  [context]
  (str (tconfig/application-public-root-url context) "acls/"))

(def base-fields
  ["concept-id" "revision-id" "display-name" "identity-type"])

(def fields-with-full-acl
  (conj base-fields "acl-gzip-b64"))

(defmethod elastic-search-index/concept-type+result-format->fields [:acl :json]
  [_concept-type query]
  (if (some #{:include-full-acl} (:result-features query))
    fields-with-full-acl
    base-fields))

(defmethod elastic-results/elastic-result->query-result-item [:acl :json]
  [context _query elastic-result]
  (let [result-source (:_source elastic-result)
        item (if-let [acl-gzip (:acl-gzip-b64 result-source)]
               (-> result-source
                   (assoc :acl (edn/read-string (util/gzip-base64->string acl-gzip)))
                   (dissoc :acl-gzip-b64))
               result-source)]
    (-> item
        (set/rename-keys {:display-name :name})
        (assoc :location (str (reference-root context) (:concept-id item)))
        util/remove-nil-keys)))

(defmethod qs/search-results->response [:acl :json]
  [_context _query results]
  (let [results (select-keys results [:hits :took :items])]
    (json/generate-string (util/map-keys->snake_case results))))
