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
            [cmr.common-app.api.routes :as common-routes]
            [cmr.common-app.api-docs :as api-docs]
            [cmr.access-control.services.group-service :as group-service]))

(def ^:private group-schema-structure
  "Schema for groups as json."
  {:type :object
   :additionalProperties false
   :properties {:name {:type :string :minLength 1 :maxLength 100}
                :provider-id {:type :string :minLength 1 :maxLength 50}
                :description {:type :string :minLength 1 :maxLength 255}
                :legacy-guid {:type :string :minLength 1 :maxLength 50}}
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

(defn- validate-group-json
  "Validates the group JSON string against the schema. Throws a service error if it is invalid."
  [json-str]
  (when-let [errors (seq (js/validate-json group-schema json-str))]
    (errors/throw-service-errors :bad-request errors)))

(defn- validate-group-members-json
  "Validates the group mebers JSON string against the schema. Throws a service error if it is invalid."
  [json-str]
  (when-let [errors (seq (js/validate-json group-members-schema json-str))]
    (errors/throw-service-errors :bad-request errors)))

(defn create-group
  "Processes a create group request."
  [context headers body]
  ;; TODO CMR-2133, CMR-2134 - verify permission in service (dependent on provider level or system level)
  (validate-content-type headers)
  (validate-group-json body)
  (->> (json/parse-string body true)
       (group-service/create-group context)
       api-response))

(defn get-group
  "Retrieves the group with the given concept-id."
  [context concept-id]
  (-> (group-service/get-group context concept-id)
      api-response))

(defn update-group
  "Processes a request to update a group."
  [context headers body concept-id]
  (validate-content-type headers)
  (validate-group-json body)
  (->> (json/parse-string body true)
       (group-service/update-group context concept-id)
       api-response))

(defn delete-group
  "Deletes the group with the given concept-id."
  [context concept-id]
  (api-response (group-service/delete-group context concept-id)))

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
       api-response))

(defn remove-members
  "Handles a request to remove group members"
  [context headers body concept-id]
  (validate-content-type headers)
  (validate-group-members-json body)
  (->> (json/parse-string body true)
       (group-service/remove-members context concept-id)
       api-response))

;; TODO this should be reusable
(def CONTENT_TYPE_HEADER "Content-Type")
(def HITS_HEADER "CMR-Hits")
(def TOOK_HEADER "CMR-Took")
(def CORS_ORIGIN_HEADER
  "This CORS header is to restrict access to the resource to be only from the defined origins,
  value of \"*\" means all request origins have access to the resource"
  "Access-Control-Allow-Origin")
;; TODO also get and make the options response work
;; Why did we add that in the first place? Does it make sense that we only support it in one spot?


(defn- search-response-headers
  "Generate headers for search response."
  [content-type results]
  (merge {CONTENT_TYPE_HEADER (mt/with-utf-8 content-type)
          CORS_ORIGIN_HEADER "*"}
         (when (:hits results) {HITS_HEADER (str (:hits results))})
         (when (:took results) {TOOK_HEADER (str (:took results))})))

(defn- search-response
  "Generate the response map for finding concepts/"
  [{:keys [results result-format] :as response}]
  {:status 200
   :headers (search-response-headers (mt/format->mime-type result-format) response)
   :body results})

(defn search-for-groups
  [context params]
  ;; TODO CMR-2130 validate accept header (only accept JSON search response)
  (-> (group-service/search-for-groups context params)
      search-response))


(def admin-api-routes
  "The administrative control routes."
  (routes
    (POST "/reset" {:keys [request-context params headers]}
      (acl/verify-ingest-management-permission request-context :update)
      (cache/reset-caches request-context)
      {:status 204})))

(defn- build-routes [system]
  (routes
    (context (:relative-root-url system) []
      admin-api-routes

      ;; Add routes for API documentation
      (api-docs/docs-routes (get-in system [:public-conf :protocol])
                            (get-in system [:public-conf :relative-root-url])
                            "public/access_control_index.html")

      ;; add routes for checking health of the application
      (common-routes/health-api-routes group-service/health)

      (context "/groups" []

        ;; TODO CMR-2130 document in api docs
        ;; Search for groups
        (GET "/" {:keys [request-context params]}
          (search-for-groups request-context params))

        ;; Create a group
        (POST "/" {:keys [request-context headers body]}
          ;; TEMPORARY ACL CHECK UNTIL REAL ONE IS IMPLEMENTED
          (acl/verify-ingest-management-permission request-context :update)
          (create-group request-context headers (slurp body)))

        (context "/:group-id" [group-id]
          ;; Get a group
          (GET "/" {:keys [request-context]}
            (get-group request-context group-id))

          ;; Delete a group
          (DELETE "/" {:keys [request-context]}
            ;; TEMPORARY ACL CHECK UNTIL REAL ONE IS IMPLEMENTED
            (acl/verify-ingest-management-permission request-context :update)
            (delete-group request-context group-id))

          ;; Update a group
          (PUT "/" {:keys [request-context headers body]}
            ;; TEMPORARY ACL CHECK UNTIL REAL ONE IS IMPLEMENTED
            (acl/verify-ingest-management-permission request-context :update)
            (update-group request-context headers (slurp body) group-id))

          (context "/members" []
            (GET "/" {:keys [request-context]}
              (get-members request-context group-id))

            (POST "/" {:keys [request-context headers body]}
              ;; TEMPORARY ACL CHECK UNTIL REAL ONE IS IMPLEMENTED
              (acl/verify-ingest-management-permission request-context :update)
              (add-members request-context headers (slurp body) group-id))

            (DELETE "/" {:keys [request-context headers body]}
              ;; TEMPORARY ACL CHECK UNTIL REAL ONE IS IMPLEMENTED
              (acl/verify-ingest-management-permission request-context :update)
              (remove-members request-context headers (slurp body) group-id))))))

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



