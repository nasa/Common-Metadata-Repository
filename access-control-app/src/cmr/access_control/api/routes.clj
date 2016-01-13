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

(defn- api-response
  "Creates a successful response with the given data response"
  ([data]
   (api-response data true))
  ([data encode?]
   {:status 200
    :body (if encode? (json/generate-string data) data)
    :headers {"Content-Type" mt/json}}))

(defn- validate-group-content-type
  "Validates that content type sent with a group is JSON"
  [headers]
  (mt/extract-header-mime-type #{mt/json} headers "content-type" true))

(defn- validate-group-json
  "Validates the group JSON string against the schema. Throws a service error if it is invalid."
  [json-str]
  (when-let [errors (seq (js/validate-json group-schema json-str))]
    (errors/throw-service-errors :bad-request errors)))

(defn create-group
  "Processes a create group request."
  [context headers body]
  ;; TODO CMR-2133, CMR-2134 - verify permission in service (dependent on provider level or system level)
  (validate-group-content-type headers)
  (validate-group-json body)
  (->> (json/parse-string body true)
       (group-service/create-group context)
       api-response))

(defn get-group
  "Retrieves the group with the given concept-id."
  [context concept-id]
  (-> (group-service/get-group context concept-id)
      api-response))


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

      (context "/groups" []
        (POST "/" {:keys [request-context headers body]}
          ;; TEMPORARY ACL CHECK UNTIL REAL ONE IS IMPLEMENTED
          (acl/verify-ingest-management-permission request-context :update)
          (create-group request-context headers (slurp body)))

        (context "/:group-id" [group-id]
          ;; Get a group
          (GET "/" {:keys [request-context]}
            (get-group request-context group-id)))))

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



