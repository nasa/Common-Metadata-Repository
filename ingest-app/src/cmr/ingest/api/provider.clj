(ns cmr.ingest.api.provider
  "Defines the HTTP URL routes for the provider endpoint in the ingest application."
  (:require
   [cheshire.core :as json]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as srvc-errors]
   [cmr.ingest.services.provider-service :as ps]
   [compojure.core :refer :all]))

(defn- result->response-map
  "Returns the response map of the given result"
  [result]
  (let [{:keys [status body]} result]
    {:status status
     :headers {"Content-Type" (mt/with-utf-8 mt/json)}
     :body body}))

(defn read-body
  [headers body-input]
  (if (= mt/json (mt/content-type-mime-type headers))
    (json/decode (slurp body-input) true)
    (srvc-errors/throw-service-error
      :invalid-content-type "Creating or updating a provider requires a JSON content type.")))

(def provider-api-routes
  (context "/providers" []

    ;; create a new provider
    (POST "/" {:keys [request-context body params headers]}
      (acl/verify-ingest-management-permission request-context :update)
      (common-enabled/validate-write-enabled request-context "ingest")
      (result->response-map
        (ps/create-provider request-context (read-body headers body))))

    ;; update an existing provider
    (PUT "/:provider-id" {{:keys [provider-id] :as params} :params
                          request-context :request-context
                          body :body
                          headers :headers}
      (acl/verify-ingest-management-permission request-context :update)
      (common-enabled/validate-write-enabled request-context "ingest")
      (result->response-map
        (ps/update-provider request-context (read-body headers body))))

    ;; delete a provider
    (DELETE "/:provider-id" {{:keys [provider-id] :as params} :params
                             request-context :request-context
                             headers :headers}
      (acl/verify-ingest-management-permission request-context :update)
      (common-enabled/validate-write-enabled request-context "ingest")
      (ps/verify-empty-provider request-context provider-id headers)
      (result->response-map
        (ps/delete-provider request-context provider-id)))

    ;; get a list of providers
    (GET "/" {:keys [request-context]}
      (result->response-map
        (ps/get-providers-raw request-context)))))
