(ns cmr.access-control.api.routes
  "Defines the HTTP URL routes for the application."
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [cmr.access-control.data.access-control-index :as index]
    [cmr.access-control.data.acl-schema :as acl-schema]
    [cmr.access-control.services.acl-search-service :as acl-search]
    [cmr.access-control.services.acl-service :as acl-service]
    [cmr.access-control.services.group-service :as group-service]
    [cmr.access-control.test.bootstrap :as bootstrap]
    [cmr.acl.core :as acl]
    [cmr.common-app.api-docs :as api-docs]
    [cmr.common-app.api.routes :as common-routes]
    [cmr.common.api.context :as context]
    [cmr.common.api.errors :as api-errors]
    [cmr.common.cache :as cache]
    [cmr.common.concepts :as cc]
    [cmr.common.log :refer (debug info warn error)]
    [cmr.common.mime-types :as mt]
    [cmr.common.services.errors :as errors]
    [cmr.common.util :as util]
    [cmr.common.validations.core :as validation]
    [cmr.common.validations.json-schema :as js]
    [compojure.core :refer :all]
    [compojure.handler :as handler]
    [compojure.route :as route]
    [ring.middleware.json :as ring-json]
    [ring.middleware.keyword-params :as keyword-params]
    [ring.middleware.nested-params :as nested-params]
    [ring.middleware.params :as params]))

;;; Utility Functions

(defn- validate-params
  "Throws a service error when any keys exist in params other than those in allowed-param-names."
  [params & allowed-param-names]
  (when-let [invalid-params (seq (remove (set allowed-param-names) (keys params)))]
    (errors/throw-service-errors :bad-request (for [param invalid-params]
                                                (format "Parameter [%s] was not recognized."
                                                        (name param))))))

(defn- validate-standard-params
  "Throws a service error if any parameters other than :token or :pretty are present."
  [params]
  (validate-params params :pretty :token))

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

;;; Group Schema and Params Utils

(def ^:private group-schema-structure
  "Schema for groups as json."
  {:type :object
   :additionalProperties false
   :properties {:name {:type :string :minLength 1 :maxLength 100}
                :provider_id {:type :string :minLength 1 :maxLength 50}
                :description {:type :string :minLength 1 :maxLength 255}
                :legacy_guid {:type :string :minLength 1 :maxLength 50}
                :members {:type :array :items {:type :string :minLength 1 :maxLength 100}}}
   :required [:name :description]})

(def ^:private group-schema
  "The JSON schema used to validate groups"
  (js/parse-json-schema group-schema-structure))

(def ^:private group-members-schema-structure
  "Schema defining list of usernames sent to add or remove members in a group"
  {:type :array :items {:type :string :minLength 1 :maxLength 50}})

(def ^:private group-members-schema
  "The JSON schema used to validate a list of group members"
  (js/parse-json-schema group-members-schema-structure))

(defn- validate-group-route-params
  "Same as validate-standard-params plus :group-id."
  [params]
  (validate-params params :pretty :token :group-id))

(defn- validate-group-json
  "Validates the group JSON string against the schema. Throws a service error if it is invalid."
  [json-str]
  (js/validate-json! group-schema json-str))

(defn- validate-group-members-json
  "Validates the group mebers JSON string against the schema. Throws a service error if it is invalid."
  [json-str]
  (js/validate-json! group-members-schema json-str))

;; Misc route validations

(defn system_object-concept_id-provider-target-validation
  "Validates presence and combinations of system_object, concept_id, provider, and target parameters."
  [{:keys [system_object concept_id provider target]}]
  (let [present? #(if (string? %)
                   (not (str/blank? %))
                   (seq %))]
    (when-not (util/xor (present? system_object)
                        (present? concept_id)
                        (and (present? provider)
                             (present? target)))
      ["One of [concept_id], [system_object], or [provider] and [target] are required."])))

(defn system_object-validation
  "Validates that system_object parameter has a valid value, if present."
  [{:keys [system_object]}]
  (when system_object
    (when-not (some #{system_object} acl-schema/system-object-targets)
      [(str "Parameter [system_object] must be one of: " (pr-str acl-schema/system-object-targets))])))

(defn concept_ids-validation
  "Validates that all values in the multi-valued concept_id param are valid concept IDs"
  [{:keys [concept_id]}]
  (mapcat cc/concept-id-validation concept_id))

(defn user_id-user_type-validation
  "Validates that only one of user_id or user_type are specified."
  [{:keys [user_id user_type]}]
  (if-not (= 1 (count (remove str/blank? [user_id user_type])))
    ["One of parameters [user_type] or [user_id] are required."]))

(defn provider-target-validation
  "Validates that when provider param is specified, target param is a valid enum value."
  [{:keys [provider target]}]
  (when (and provider (not (some #{target} acl-schema/provider-object-targets)))
    [(str "Parameter [target] must be one of: " (pr-str acl-schema/provider-object-targets))]))

(def get-permissions-validations
  [system_object-concept_id-provider-target-validation
   provider-target-validation
   user_id-user_type-validation
   system_object-validation
   concept_ids-validation])

(defn- validate-get-permission-params
  "Throws service errors if any invalid params or values are found."
  [params]
  (validate-params params :system_object :concept_id :user_id :user_type :provider :target)
  (when-let [errors (seq (mapcat #(% params) get-permissions-validations))]
    (errors/throw-service-errors :bad-request errors)))

;;; Group Route Functions

(defn create-group
  "Processes a create group request."
  [context headers body]
  (validate-content-type headers)
  (validate-group-json body)
  (->> (json/parse-string body true)
       (util/map-keys->kebab-case)
       (group-service/create-group context)
       (util/map-keys->snake_case)
       api-response))

(defn get-group
  "Retrieves the group with the given concept-id."
  [context concept-id]
  (-> (group-service/get-group context concept-id)
      (util/map-keys->snake_case)
      api-response))

(defn update-group
  "Processes a request to update a group."
  [context headers body concept-id]
  (validate-content-type headers)
  (validate-group-json body)
  (->> (json/parse-string body true)
       (util/map-keys->kebab-case)
       (group-service/update-group context concept-id)
       (util/map-keys->snake_case)
       api-response))

(defn delete-group
  "Deletes the group with the given concept-id."
  [context concept-id]
  (api-response (util/map-keys->snake_case (group-service/delete-group context concept-id))))

(defn get-members
  "Handles a request to fetch group members"
  [context concept-id]
  (api-response (group-service/get-members context concept-id)))

(defn add-members
  "Handles a request to add group members"
  [context headers body concept-id]
  (validate-content-type headers)
  (validate-group-members-json body)
  (->> (json/parse-string body true)
       (group-service/add-members context concept-id)
       (util/map-keys->snake_case)
       api-response))

(defn remove-members
  "Handles a request to remove group members"
  [context headers body concept-id]
  (validate-content-type headers)
  (validate-group-members-json body)
  (->> (json/parse-string body true)
       (group-service/remove-members context concept-id)
       (util/map-keys->snake_case)
       api-response))

(defn search-for-groups
  [context headers params]
  (mt/extract-header-mime-type #{mt/json mt/any} headers "accept" true)
  (-> (group-service/search-for-groups context (dissoc params :token))
      common-routes/search-response))

;;; ACL Route Functions

(defn create-acl
  "Returns a Ring response with the result of trying to create the ACL with the given request body."
  [request-context headers body]
  (validate-content-type headers)
  (js/validate-json! acl-schema/acl-schema body)
  (->> (json/parse-string body)
       util/map-keys->kebab-case
       (acl-service/create-acl request-context)
       util/map-keys->snake_case
       api-response))

(defn update-acl
  "Returns a Ring response with the result of trying to update the ACL with the given concept id
  and request body."
  [request-context concept-id headers body]
  (validate-content-type headers)
  (js/validate-json! acl-schema/acl-schema body)
  (->> (json/parse-string body)
       util/map-keys->kebab-case
       (acl-service/update-acl request-context concept-id)
       util/map-keys->snake_case
       api-response))

(defn delete-acl
  "Returns a Ring response with the result of trying to delete the ACL with the given concept id."
  [request-context concept-id]
  (api-response (acl-service/delete-acl request-context concept-id)))

(defn get-acl
  "Returns a Ring response with the metadata of the ACL identified by concept-id."
  [request-context headers concept-id]
  (-> (acl-service/get-acl request-context concept-id)
      (util/map-keys->snake_case)
      api-response))

(defn search-for-acls
  "Returns a Ring response with ACL search results for the given params."
  [context headers params]
  (mt/extract-header-mime-type #{mt/json mt/any} headers "accept" true)
  (-> (acl-search/search-for-acls context params)
      common-routes/search-response))

(defn get-permissions
  "Returns a Ring response with the requested permission check results."
  [request-context params]
  (let [params (update-in params [:concept_id] util/seqify)]
    (validate-get-permission-params params)
    (let [{:keys [user_id user_type concept_id system_object provider target]} params
          username-or-type (if user_type
                             (keyword user_type)
                             user_id)
          result (cond
                   system_object (acl-service/get-system-permissions request-context username-or-type system_object)
                   target (acl-service/get-provider-permissions request-context username-or-type provider target)
                   :else (acl-service/get-catalog-item-permissions request-context username-or-type concept_id))]
      {:status 200
       :body (json/generate-string result)})))

(defn reindex-groups
  "Processes a request to reindex all groups"
  [context]
  (index/reindex-groups context)
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
      {:status 204})))

;;; Handler

(defn- build-routes [system]
  (routes
    (context (:relative-root-url system) []
      ;; for NGAP deployment health check
      (GET "/" {} {:status 200})
      admin-api-routes

      ;; Add routes for API documentation
      (api-docs/docs-routes (get-in system [:public-conf :protocol])
                            (get-in system [:public-conf :relative-root-url])
                            "public/access_control_index.html")

      ;; add routes for checking health of the application
      (common-routes/health-api-routes group-service/health)

      ;; add routes for accessing caches
      common-routes/cache-api-routes

      ;; Reindex all groups
      (POST "/reindex-groups" {:keys [request-context headers params]}
        (acl/verify-ingest-management-permission request-context :update)
        (validate-standard-params params)
        (reindex-groups request-context))

      (context "/groups" []
        (OPTIONS "/" req
                 (validate-standard-params (:params req))
                 common-routes/options-response)

        ;; Search for groups
        (GET "/" {:keys [request-context headers params]}
          (search-for-groups request-context headers params))

        ;; Create a group
        (POST "/" {:keys [request-context headers body params]}
          (validate-standard-params params)
          (create-group request-context headers (slurp body)))

        (context "/:group-id" [group-id]
          (OPTIONS "/" req common-routes/options-response)
          ;; Get a group
          (GET "/" {:keys [request-context params]}
            (validate-group-route-params params)
            (get-group request-context group-id))

          ;; Delete a group
          (DELETE "/" {:keys [request-context params]}
            (validate-group-route-params params)
            (delete-group request-context group-id))

          ;; Update a group
          (PUT "/" {:keys [request-context headers body params]}
            (validate-group-route-params params)
            (update-group request-context headers (slurp body) group-id))

          (context "/members" []
            (OPTIONS "/" req common-routes/options-response)
            (GET "/" {:keys [request-context params]}
              (validate-group-route-params params)
              (get-members request-context group-id))

            (POST "/" {:keys [request-context headers body params]}
              (validate-group-route-params params)
              (add-members request-context headers (slurp body) group-id))

            (DELETE "/" {:keys [request-context headers body params]}
              (validate-group-route-params params)
              (remove-members request-context headers (slurp body) group-id)))))

      (context "/acls" []
        (OPTIONS "/" req
                 (validate-standard-params (:params req))
                 common-routes/options-response)

        ;; Search for ACLs
        (GET "/" {:keys [request-context headers params]}
          (search-for-acls request-context headers params))

        ;; Create an ACL
        (POST "/" {:keys [request-context headers body params]}
          (validate-standard-params params)
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
            (get-acl request-context headers concept-id))))

      (context "/permissions" []
        (OPTIONS "/" [] common-routes/options-response)

        (GET "/" {:keys [request-context params]}
          (get-permissions request-context params))))

    (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      acl/add-authentication-handler
      common-routes/add-request-id-response-handler
      (context/build-request-context-handler system)
      keyword-params/wrap-keyword-params
      nested-params/wrap-nested-params
      api-errors/invalid-url-encoding-handler
      api-errors/exception-handler
      common-routes/pretty-print-response-handler
      params/wrap-params))
