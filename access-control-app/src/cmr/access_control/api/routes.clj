(ns cmr.access-control.api.routes
  "Defines the HTTP URL routes for the application."
  (:require
   [cheshire.core :as json]
   [cmr.access-control.data.access-control-index :as index]
   [cmr.access-control.data.acl-schema :as acl-schema]
   [cmr.access-control.data.group-schema :as group-schema]
   [cmr.access-control.services.acl-search-service :as acl-search]
   [cmr.access-control.services.acl-service :as acl-service]
   [cmr.access-control.services.group-service :as group-service]
   [cmr.access-control.services.parameter-validation :as pv]
   [cmr.access-control.test.bootstrap :as bootstrap]
   [cmr.acl.core :as acl]
   [cmr.common-app.api-docs :as api-docs]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.health :as common-health]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common.api.context :as context]
   [cmr.common.api.errors :as api-errors]
   [cmr.common.cache :as cache]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.common.validations.core :as validation]
   [compojure.core :refer :all]
   [compojure.handler :as handler]
   [compojure.route :as route]
   [ring.middleware.json :as ring-json]
   [ring.middleware.keyword-params :as keyword-params]
   [ring.middleware.nested-params :as nested-params]
   [ring.middleware.params :as params]))

(defn- api-response
  "Creates a successful response with the given data response"
  ([data]
   (api-response data true))
  ([data encode?]
   {:status 200
    :body (if encode? (json/generate-string data) data)
    :headers {"Content-Type" mt/json}}))

(defn- validate-content-type
  "Validates that content type sent is JSON"
  [headers]
  (mt/extract-header-mime-type #{mt/json} headers "content-type" true))

(defn- create-group-with-managing-group
  "Helper function to invoke group service create-group function to pass in a managing group id."
  [context managing-group-id group]
  (group-service/create-group context group {:managing-group-id managing-group-id}))

(defn- create-group
  "Processes a create group request."
  [context headers body managing-group-id]
  (validate-content-type headers)
  (group-schema/validate-group-json body)
  (->> (json/parse-string body true)
       (util/map-keys->kebab-case)
       (create-group-with-managing-group context managing-group-id)
       (util/map-keys->snake_case)
       api-response))

(defn- get-group
  "Retrieves the group with the given concept-id."
  [context concept-id]
  (-> (group-service/get-group context concept-id)
      (util/map-keys->snake_case)
      api-response))

(defn- update-group
  "Processes a request to update a group."
  [context headers body concept-id]
  (validate-content-type headers)
  (group-schema/validate-group-json body)
  (->> (json/parse-string body true)
       (util/map-keys->kebab-case)
       (group-service/update-group context concept-id)
       (util/map-keys->snake_case)
       api-response))

(defn- delete-group
  "Deletes the group with the given concept-id."
  [context concept-id]
  (api-response (util/map-keys->snake_case (group-service/delete-group context concept-id))))

(defn- get-members
  "Handles a request to fetch group members"
  [context concept-id]
  (api-response (group-service/get-members context concept-id)))

(defn- add-members
  "Handles a request to add group members"
  [context headers body concept-id]
  (validate-content-type headers)
  (group-schema/validate-group-members-json body)
  (->> (json/parse-string body true)
       (group-service/add-members context concept-id)
       (util/map-keys->snake_case)
       api-response))

(defn- remove-members
  "Handles a request to remove group members"
  [context headers body concept-id]
  (validate-content-type headers)
  (group-schema/validate-group-members-json body)
  (->> (json/parse-string body true)
       (group-service/remove-members context concept-id)
       (util/map-keys->snake_case)
       api-response))

(defn- search-for-groups
  [context headers params]
  (mt/extract-header-mime-type #{mt/json mt/any} headers "accept" true)
  (-> (group-service/search-for-groups context (dissoc params :token))
      common-routes/search-response))

;;; ACL Route Functions

(defn- create-acl
  "Returns a Ring response with the result of trying to create the ACL with the given request body."
  [request-context headers body]
  (validate-content-type headers)
  (acl-schema/validate-acl-json body)
  (->> (json/parse-string body)
       util/map-keys->kebab-case
       (acl-service/create-acl request-context)
       util/map-keys->snake_case
       api-response))

(defn- update-acl
  "Returns a Ring response with the result of trying to update the ACL with the given concept id
  and request body."
  [request-context concept-id headers body]
  (validate-content-type headers)
  (acl-schema/validate-acl-json body)
  (->> (json/parse-string body)
       util/map-keys->kebab-case
       (acl-service/update-acl request-context concept-id)
       util/map-keys->snake_case
       api-response))

(defn- delete-acl
  "Returns a Ring response with the result of trying to delete the ACL with the given concept id."
  [request-context concept-id]
  (api-response (acl-service/delete-acl request-context concept-id)))

(defn- get-acl
  "Returns a Ring response with the metadata of the ACL identified by concept-id."
  [request-context headers concept-id params]
  (-> (acl-service/get-acl request-context concept-id params)
      (util/map-keys->snake_case)
      api-response))

(defn- search-for-acls
  "Returns a Ring response with ACL search results for the given params."
  [context headers params]
  (mt/extract-header-mime-type #{mt/json mt/any} headers "accept" true)
  (-> (acl-search/search-for-acls context params)
      common-routes/search-response))

(defn- get-permissions
  "Returns a Ring response with the requested permission check results."
  [request-context params]
  (let [result (acl-service/get-permissions request-context params)]
    {:status 200
     :body (json/generate-string result)}))

(defn- reindex-groups
  "Processes a request to reindex all groups"
  [context]
  (index/reindex-groups context)
  {:status 200})

(defn- reindex-acls
  "Processes a request to reindex all acls"
  [context]
  (index/reindex-acls context)
  {:status 200})

;;; Various Admin Route Functions

(defn reset
  "Resets the app state. Compatible with cmr.dev-system.control."
  ([context]
   (reset context true))
  ([context bootstrap-data]
   (cache/reset-caches context)
   (index/reset (-> context :system :search-index))
   (when bootstrap-data
     (bootstrap/bootstrap (:system context)))))

(def admin-api-routes
  "The administrative control routes."
  (routes
    (POST "/reset" {:keys [request-context params headers]}
      (acl/verify-ingest-management-permission request-context :update)
      (reset request-context (= (:bootstrap_data params) "true"))
      {:status 204})
    (POST "/db-migrate" {context :request-context}
      (acl/verify-ingest-management-permission context :update)
      (index/create-index-or-update-mappings (-> context :system :search-index))
      {:status 204})))

;;; Handler

(defn build-routes [system]
  (routes
    (context (:relative-root-url system) []
      admin-api-routes

      ;; add routes for checking health of the application
      (common-health/health-api-routes group-service/health)

       ;; add routes for enabling/disabling writes
      (common-enabled/write-enabled-api-routes
       #(acl/verify-ingest-management-permission % :update))

      ;; add routes for accessing caches
      common-routes/cache-api-routes

      ;; Reindex all groups
      (POST "/reindex-groups" {:keys [request-context headers params]}
        (acl/verify-ingest-management-permission request-context :update)
        (pv/validate-standard-params params)
        (reindex-groups request-context))

      ;; Reindex all acls
      (POST "/reindex-acls" {:keys [request-context headers params]}
        (acl/verify-ingest-management-permission request-context :update)
        (pv/validate-standard-params params)
        (reindex-acls request-context))

      (context "/groups" []
        (OPTIONS "/" req
                 (pv/validate-standard-params (:params req))
                 common-routes/options-response)

        ;; Search for groups
        (GET "/" {:keys [request-context headers params]}
          (search-for-groups request-context headers params))

        ;; Create a group
        (POST "/" {:keys [request-context headers body params]}
          (pv/validate-create-group-route-params params)
          (create-group request-context headers (slurp body)
                        (or (:managing-group-id params) (:managing_group_id params))))

        (context "/:group-id" [group-id]
          (OPTIONS "/" req common-routes/options-response)
          ;; Get a group
          (GET "/" {:keys [request-context params]}
            (pv/validate-group-route-params params)
            (get-group request-context group-id))

          ;; Delete a group
          (DELETE "/" {:keys [request-context params]}
            (pv/validate-group-route-params params)
            (delete-group request-context group-id))

          ;; Update a group
          (PUT "/" {:keys [request-context headers body params]}
            (pv/validate-group-route-params params)
            (update-group request-context headers (slurp body) group-id))

          (context "/members" []
            (OPTIONS "/" req common-routes/options-response)
            (GET "/" {:keys [request-context params]}
              (pv/validate-group-route-params params)
              (get-members request-context group-id))

            (POST "/" {:keys [request-context headers body params]}
              (pv/validate-group-route-params params)
              (add-members request-context headers (slurp body) group-id))

            (DELETE "/" {:keys [request-context headers body params]}
              (pv/validate-group-route-params params)
              (remove-members request-context headers (slurp body) group-id)))))

      (context "/acls" []
        (OPTIONS "/" req
                 (pv/validate-standard-params (:params req))
                 common-routes/options-response)

        ;; Search for ACLs with either GET or POST
        (GET "/" {:keys [request-context headers params]}
          (search-for-acls request-context headers params))
        ;; POST search is at a different route to avoid a collision with the ACL creation route
        (POST "/search" {:keys [request-context headers params]}
          (search-for-acls request-context headers params))

        ;; Create an ACL
        (POST "/" {:keys [request-context headers body params]}
          (pv/validate-standard-params params)
          (create-acl request-context headers (slurp body)))

        (context "/:concept-id" [concept-id]
          (OPTIONS "/" req common-routes/options-response)

          ;; Update an ACL
          (PUT "/" {:keys [request-context headers body]}
            (update-acl request-context concept-id headers (slurp body)))

          ;; Delete an ACL
          (DELETE "/" {:keys [request-context]}
            (delete-acl request-context concept-id))

          ;; Retrieve an ACL
          (GET "/" {:keys [request-context headers params]}
            (get-acl request-context headers concept-id params))))

      (context "/permissions" []
        (OPTIONS "/" [] common-routes/options-response)

        (GET "/" {:keys [request-context params]}
          (get-permissions request-context params))))))
