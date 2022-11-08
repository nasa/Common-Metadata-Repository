(ns cmr.access-control.api.routes
  "Defines the HTTP URL routes for the access-control API."
  (:require
   [cheshire.core :as json]
   [cmr.access-control.data.access-control-index :as index]
   [cmr.access-control.data.acl-schema :as acl-schema]
   [cmr.access-control.data.group-schema :as group-schema]
   [cmr.access-control.services.acl-search-service :as acl-search]
   [cmr.access-control.services.acl-service :as acl-service]
   [cmr.access-control.services.acl-util :as acl-util]
   [cmr.access-control.services.group-service :as group-service]
   [cmr.access-control.services.parameter-validation :as pv]
   [cmr.access-control.test.bootstrap :as bootstrap]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.health :as common-health]
   [cmr.common-app.api.launchpad-token-validation :as lt-validation]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common.api.errors :as api-errors]
   [cmr.common.cache :as cache]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.common.validations.core :as validation]
   [compojure.core :refer :all]
   [compojure.handler :as handler]
   [ring.middleware.keyword-params :as keyword-params]
   [ring.middleware.nested-params :as nested-params]
   [ring.middleware.params :as params])
  (:import
   (org.json JSONException)))

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
  [ctx managing-group-id group]
  (group-service/create-group ctx group {:managing-group-id managing-group-id}))

(defn- create-group
  "Processes a create group request."
  [ctx headers body managing-group-id]
  (validate-content-type headers)
  (group-schema/validate-group-json body)
  (->> (json/parse-string body true)
       (util/map-keys->kebab-case)
       (create-group-with-managing-group ctx managing-group-id)
       (util/map-keys->snake_case)
       api-response))

(defn- get-group
  "Retrieves the group with the given concept-id."
  [ctx concept-id]
  (-> (group-service/get-group ctx concept-id)
      (util/map-keys->snake_case)
      api-response))

(defn- update-group
  "Processes a request to update a group."
  [ctx headers body concept-id]
  (validate-content-type headers)
  (group-schema/validate-group-json body)
  (->> (json/parse-string body true)
       (util/map-keys->kebab-case)
       (group-service/update-group ctx concept-id)
       (util/map-keys->snake_case)
       api-response))

(defn- delete-group
  "Deletes the group with the given concept-id."
  [ctx concept-id]
  (api-response (util/map-keys->snake_case (group-service/delete-group ctx concept-id))))

(defn- get-members
  "Handles a request to fetch group members"
  [ctx concept-id]
  (api-response (group-service/get-members ctx concept-id)))

(defn- add-members
  "Handles a request to add group members"
  [ctx headers body concept-id]
  (validate-content-type headers)
  (group-schema/validate-group-members-json body)
  (->> (json/parse-string body true)
       (group-service/add-members ctx concept-id)
       (util/map-keys->snake_case)
       api-response))

(defn- remove-members
  "Handles a request to remove group members"
  [ctx headers body concept-id]
  (validate-content-type headers)
  (group-schema/validate-group-members-json body)
  (->> (json/parse-string body true)
       (group-service/remove-members ctx concept-id)
       (util/map-keys->snake_case)
       api-response))

(defn- search-for-groups
  [ctx headers params]
  (mt/extract-header-mime-type #{mt/json mt/any} headers "accept" true)
  (-> (group-service/search-for-groups ctx (dissoc params :token))
      common-routes/search-response))

;;; ACL Route Functions

(defn- create-acl
  "Returns a Ring response with the result of trying to create the ACL with the given request body."
  [request-ctx headers body]
  (validate-content-type headers)
  (acl-schema/validate-acl-json body)
  (try
    (->> (json/parse-string body)
         util/map-keys->kebab-case
         (acl-service/create-acl request-ctx)
         util/map-keys->snake_case
         api-response)
    (catch com.fasterxml.jackson.core.JsonParseException e
      (errors/throw-service-error :bad-request (str "Json parsing error: " (.getMessage e))))))

(defn- update-acl
  "Returns a Ring response with the result of trying to update the ACL with the given concept id
  and request body."
  [ctx concept-id headers body]
  (validate-content-type headers)
  (acl-schema/validate-acl-json body)
  (->> (json/parse-string body)
       util/map-keys->kebab-case
       (acl-service/update-acl ctx concept-id (get headers "cmr-revision-id"))
       util/map-keys->snake_case
       api-response))

(defn- delete-acl
  "Returns a Ring response with the result of trying to delete the ACL with the given concept id."
  [ctx concept-id headers]
  (api-response (acl-service/delete-acl ctx concept-id (get headers "cmr-revision-id"))))

(defn- get-acl
  "Returns a Ring response with the metadata of the ACL identified by concept-id."
  [ctx headers concept-id params]
  (-> (acl-service/get-acl ctx concept-id params)
      (util/map-keys->snake_case)
      api-response))

(defn- search-for-acls
  "Returns a Ring response with ACL search results for the given params."
  [ctx headers params]
  (mt/extract-header-mime-type #{mt/json mt/any} headers "accept" true)
  (-> (acl-search/search-for-acls ctx params)
      common-routes/search-response))

(defn- get-permissions
  "Returns a Ring response with the requested permission check results."
  [ctx params]
  (let [result (acl-service/get-permissions ctx params)]
    {:status 200
     :body (json/generate-string result)}))

(defn- get-current-sids
  "Returns a Ring response with the current user's sids"
  [request-context params]
  (pv/validate-current-sids-params params)
  (let [result (acl-service/get-current-sids request-context params)]
    {:status 200
     :body (json/generate-string result)}))

(defn- reindex-groups
  "Processes a request to reindex all groups"
  [ctx]
  (index/reindex-groups ctx)
  {:status 200})

(defn- reindex-acls
  "Processes a request to reindex all acls"
  [ctx]
  (index/reindex-acls ctx)
  {:status 200})

;;; Various Admin Route Functions

(defn reset
  "Resets the app state. Compatible with cmr.dev-system.control."
  ([ctx]
   (reset ctx true))
  ([ctx bootstrap-data]
   (cache/reset-caches ctx)
   (index/reset (-> ctx :system :search-index))
   (when bootstrap-data
     (bootstrap/bootstrap (:system ctx)))))

(def admin-api-routes
  "The administrative control routes."
  (routes
    (POST "/reset" {ctx :request-context params :params}
      (acl/verify-ingest-management-permission ctx :update)
      (reset ctx (= (:bootstrap_data params) "true"))
      {:status 204})
    (POST "/db-migrate" {ctx :request-context}
      (acl/verify-ingest-management-permission ctx :update)
      (index/create-index-or-update-mappings (-> ctx :system :search-index))
      {:status 204})))

;;; S3 routes

(defn- get-allowed-s3-buckets
  "Returns a list of S3 buckets the given user_id has access to."
  [context params]
  (pv/validate-s3-buckets-params params)
  (let [{user :user_id providers :provider} params
        s3-list (acl-service/s3-buckets-for-user
                 context
                 user
                 providers)]
    {:status 200
     :body (json/generate-string s3-list)}))

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
      (POST "/reindex-groups"
            {ctx :request-context params :params}
            (acl/verify-ingest-management-permission ctx :update)
            (pv/validate-standard-params params)
            (reindex-groups ctx))

      ;; Reindex all acls
      (POST "/reindex-acls"
            {ctx :request-context params :params}
            (acl/verify-ingest-management-permission ctx :update)
            (pv/validate-standard-params params)
            (reindex-acls ctx))

      (context "/groups" []
        (OPTIONS "/"
                 {params :params}
                 (pv/validate-standard-params params)
                 (common-routes/options-response))

        ;; Search for groups
        (GET "/"
             {ctx :request-context params :params headers :headers}
             (search-for-groups ctx headers params))

        ;; Create a group
        (POST "/"
              {ctx :request-context params :params headers :headers body :body}
              (lt-validation/validate-launchpad-token ctx)
              (pv/validate-create-group-route-params params)
              (create-group ctx
                            headers
                            (slurp body)
                            (or (:managing-group-id params)
                                (:managing_group_id params))))

        (context "/:group-id" [group-id]
          (OPTIONS "/" req (common-routes/options-response))
          ;; Get a group
          (GET "/"
               {ctx :request-context params :params}
               (pv/validate-group-route-params params)
               (get-group ctx group-id))

          ;; Delete a group
          (DELETE "/"
                  {ctx :request-context params :params}
                  (lt-validation/validate-launchpad-token ctx)
                  (pv/validate-group-route-params params)
                  (delete-group ctx group-id))

          ;; Update a group
          (PUT "/"
               {ctx :request-context params :params headers :headers body :body}
               (lt-validation/validate-launchpad-token ctx)
               (pv/validate-group-route-params params)
               (update-group ctx headers (slurp body) group-id))

          (context "/members" []
            (OPTIONS "/" req (common-routes/options-response))
            (GET "/"
                 {ctx :request-context params :params}
                 (pv/validate-group-route-params params)
                 (get-members ctx group-id))

            (POST "/"
                  {ctx :request-context params :params headers :headers body :body}
                  (lt-validation/validate-launchpad-token ctx)
                  (pv/validate-group-route-params params)
                  (add-members ctx headers (slurp body) group-id))

            (DELETE "/"
                    {ctx :request-context params :params headers :headers body :body}
                    (lt-validation/validate-launchpad-token ctx)
                    (pv/validate-group-route-params params)
                    (remove-members ctx headers (slurp body) group-id)))))

      (context "/acls" []
        (OPTIONS "/"
                 {params :params}
                 (pv/validate-standard-params params)
                 (common-routes/options-response))

        ;; Search for ACLs with either GET or POST
        (GET "/"
             {ctx :request-context params :params headers :headers}
             (search-for-acls ctx headers params))
        ;; POST search is at a different route to avoid a collision with the ACL creation route
        (POST "/search"
              {ctx :request-context params :params headers :headers}
              (search-for-acls ctx headers params))

        ;; Create an ACL
        (POST "/"
              {ctx :request-context params :params headers :headers body :body}
              (lt-validation/validate-launchpad-token ctx)
              (pv/validate-standard-params params)
              (create-acl ctx headers (slurp body)))

        (context "/:concept-id" [concept-id]
          (OPTIONS "/" req (common-routes/options-response))

          ;; Update an ACL
          (PUT "/"
               {ctx :request-context headers :headers body :body}
               (lt-validation/validate-launchpad-token ctx)
               (update-acl ctx concept-id headers (slurp body)))

          ;; Delete an ACL
          (DELETE "/"
                  {ctx :request-context headers :headers}
                  (lt-validation/validate-launchpad-token ctx)
                  (delete-acl ctx concept-id headers))

          ;; Retrieve an ACL
          (GET "/"
               {ctx :request-context params :params headers :headers}
               (get-acl ctx headers concept-id params))))

      (context "/permissions" []
        (OPTIONS "/" [] (common-routes/options-response))

        (GET "/"
             {ctx :request-context params :params}
             (get-permissions ctx params))

        (POST "/"
              {ctx :request-context headers :headers params :params}
              (get-permissions ctx params)))

      (context "/current-sids" []
        (OPTIONS "/" [] (common-routes/options-response))

        (GET "/" {:keys [request-context params]}
             (get-current-sids request-context params)))

      (context "/s3-buckets" []
        (OPTIONS "/" [] (common-routes/options-response))

        (GET "/"
             {ctx :request-context params :params}
             (get-allowed-s3-buckets ctx params))))))
