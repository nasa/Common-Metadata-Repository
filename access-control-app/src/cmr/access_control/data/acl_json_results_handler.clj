(ns cmr.access-control.data.acl-json-results-handler
  "Handles extracting elasticsearch acl results and converting them into a JSON search response."
  (:require
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [clojure.set :as set]
   [cmr.access-control.data.group-fetcher :as gf]
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
  [concept-type query]
  (if (some #{:include-full-acl} (:result-features query))
    fields-with-full-acl
    base-fields))

(defn- group-concept-id->legacy-guid
  "Returns the group legacy guid for the given group concept id if applicable;
   Otherwise, returns the group concept id."
  [context group-id]
  (if-let [legacy-guid (gf/group-concept-id->legacy-guid context group-id)]
    legacy-guid
    group-id))

(defn- update-group-permission-legacy-group-guid
  "Returns the given group Permission with group id replaced with legacy group guid if applicable"
  [context group-permission]
  (if-let [group-id (:group-id group-permission)]
    (update group-permission :group-id #(group-concept-id->legacy-guid context %))
    group-permission))

(defn update-acl-legacy-group-guid
  "Returns the given acl with group id replaced with legacy group guid if applicable.
   Group id only appears in the group_id field in group_permissions of an ACL or target_id field
   of a SingleInstanceIdentity ACL."
  [context acl]
  (let [acl (update acl :group-permissions
                    #(map (partial update-group-permission-legacy-group-guid context) %))]
    (if-let [single-instance-identity (:single-instance-identity acl)]
      (update-in acl [:single-instance-identity :target-id]
                 #(group-concept-id->legacy-guid context %))
      acl)))

(defn- apply-legacy-group-guid-feature
  "Returns the acl with include-legacy-group-guid feature applied if necessary"
  [acl context query]
  (if (some #{:include-legacy-group-guid} (:result-features query))
    (update acl :acl #(update-acl-legacy-group-guid context %))
    acl))

(defmethod elastic-results/elastic-result->query-result-item [:acl :json]
  [context query elastic-result]
  (let [result-source (:_source elastic-result)
        item (if-let [acl-gzip (:acl-gzip-b64 result-source)]
               (-> result-source
                   (assoc :acl (edn/read-string (util/gzip-base64->string acl-gzip)))
                   (dissoc :acl-gzip-b64)
                   (apply-legacy-group-guid-feature context query))
               result-source)]
    (-> item
        (set/rename-keys {:display-name :name})
        (assoc :location (str (reference-root context) (:concept-id item)))
        util/remove-nil-keys)))

(defmethod qs/search-results->response [:acl :json]
  [context query results]
  (let [results (select-keys results [:hits :took :items])]
    (json/generate-string (util/map-keys->snake_case results))))
