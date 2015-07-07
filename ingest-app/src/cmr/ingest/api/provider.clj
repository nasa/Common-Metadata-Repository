(ns cmr.ingest.api.provider
  "Defines the HTTP URL routes for the application."
  (:require [clojure.walk :as walk]
            [compojure.core :refer :all]
            [cheshire.core :as json]
            [cmr.acl.core :as acl]
            [cmr.ingest.services.provider-service :as ps]))

(defn- result->response-map
  "Returns the response map of the given result"
  [result]
  (let [{:keys [status body]} result]
    {:status status :body (json/decode body true)}))

(def provider-api-routes
  (context "/providers" []
    ;; create a new provider
    (POST "/" {:keys [request-context body params headers]}
      (acl/verify-ingest-management-permission request-context :update)
      (-> (ps/create-provider request-context {:provider-id (get body "provider-id")
                                               :short-name (get body "short-name")
                                               :cmr-only (get body "cmr-only")
                                               :small (get body "small")})
          result->response-map))

    ;; update an existing provider
    (PUT "/:provider-id" {{:keys [provider-id] :as params} :params
                          request-context :request-context
                          provider :body
                          headers :headers}
      (acl/verify-ingest-management-permission request-context :update)
      (-> (ps/update-provider request-context (walk/keywordize-keys provider))
          result->response-map))

    ;; delete a provider
    (DELETE "/:provider-id" {{:keys [provider-id] :as params} :params
                             request-context :request-context
                             headers :headers}
      (acl/verify-ingest-management-permission request-context :update)
      (-> (ps/delete-provider request-context provider-id)
          result->response-map))

    ;; get a list of providers
    (GET "/" {:keys [request-context]}
      (-> (ps/get-providers-raw request-context)
          result->response-map))))
