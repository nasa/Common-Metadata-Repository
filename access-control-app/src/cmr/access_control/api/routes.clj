(ns cmr.access-control.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.middleware.params :as params]
            [ring.middleware.nested-params :as nested-params]
            [ring.middleware.keyword-params :as keyword-params]
            [ring.middleware.json :as ring-json]
            [cheshire.core :as json]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.cache :as cache]
            [cmr.common.api.errors :as api-errors]
            [cmr.common.services.errors :as errors]
            [cmr.common.api.context :as context]
            [cmr.common.validations.json-schema :as js]
            [cmr.common.mime-types :as mt]
            [cmr.acl.core :as acl]
            [cmr.common-app.api.routes :as cr]
            [cmr.common-app.api-docs :as api-docs]
            [cmr.access-control.data.access-control-index :as index]
            [cmr.access-control.data.acl-schema :as acl-schema]
            [cmr.access-control.services.acl-service :as acl-service]
            [cmr.access-control.services.group-service :as group-service]
            [cmr.common.util :as util]))

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

(defn- validate-json
  "Throws :bad-request service error if json-str fails to validate against given schema."
  [schema json-str]
  (when-let [errors (seq (js/validate-json schema json-str))]
    (errors/throw-service-errors :bad-request errors)))

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
  (validate-json group-schema json-str))

(defn- validate-group-members-json
  "Validates the group mebers JSON string against the schema. Throws a service error if it is invalid."
  [json-str]
  (validate-json group-members-schema json-str))

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
  (-> (group-service/search-for-groups context params)
      cr/search-response))

;;; ACL Route Functions

(defn create-acl
  "Returns a Ring response with the result of trying to create the ACL
  represented by the request headers and parameters"
  [request-context headers body]
  (validate-json acl-schema/acl-schema body)
  (->> (json/parse-string body)
       util/map-keys->kebab-case
       (acl-service/create-acl request-context)
       util/map-keys->snake_case
       api-response))

;;; Various Admin Route Functions

(defn reset
  "Resets the app state. Compatible with cmr.dev-system.control."
  [context]
  (cache/reset-caches context)
  (index/reset (-> context :system :search-index)))

(def admin-api-routes
  "The administrative control routes."
  (routes
    (POST "/reset" {:keys [request-context params headers]}
      (acl/verify-ingest-management-permission request-context :update)
      (reset request-context)
      {:status 204})))

;;; Handler

(defn- build-routes [system]
  (routes
    (context (:relative-root-url system) []
      admin-api-routes

      ;; Add routes for API documentation
      (api-docs/docs-routes (get-in system [:public-conf :protocol])
                            (get-in system [:public-conf :relative-root-url])
                            "public/access_control_index.html")

      ;; add routes for checking health of the application
      (cr/health-api-routes group-service/health)

      ;; add routes for accessing caches
      cr/cache-api-routes

      (context "/groups" []
        (OPTIONS "/" req
                 (validate-standard-params (:params req))
                 cr/options-response)

        ;; Search for groups
        (GET "/" {:keys [request-context headers params]}
          (search-for-groups request-context headers params))

        ;; Create a group
        (POST "/" {:keys [request-context headers body params]}
          (validate-standard-params params)
          (create-group request-context headers (slurp body)))

        (context "/:group-id" [group-id]
          (OPTIONS "/" req cr/options-response)
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
            (OPTIONS "/" req cr/options-response)
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
        (POST "/" {:keys [request-context headers body params]}
          (validate-standard-params params)
          (create-acl request-context headers (slurp body)))))

    (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      acl/add-authentication-handler
      cr/add-request-id-response-handler
      (context/build-request-context-handler system)
      keyword-params/wrap-keyword-params
      nested-params/wrap-nested-params
      api-errors/invalid-url-encoding-handler
      api-errors/exception-handler
      cr/pretty-print-response-handler
      params/wrap-params))
