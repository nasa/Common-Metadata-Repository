(ns cmr.ingest.api.provider
  "Defines the HTTP URL routes for the application."
  (:require [compojure.core :refer :all]
            [cmr.acl.core :as acl]
            [cmr.ingest.services.provider-service :as ps]))

(def provider-api-routes
  (context "/providers" []
    ;; create a new provider
    (POST "/" {:keys [request-context body params headers]}
      (let [context (acl/add-authentication-to-context request-context params headers)]
        (acl/verify-ingest-management-permission context :update)
        (ps/create-provider request-context (get body "provider-id"))))
    ;; delete a provider
    (DELETE "/:provider-id" {{:keys [provider-id] :as params} :params
                             request-context :request-context
                             headers :headers}
      (let [context (acl/add-authentication-to-context request-context params headers)]
        (acl/verify-ingest-management-permission context :update)
        (ps/delete-provider request-context provider-id)))
    ;; get a list of providers
    (GET "/" {:keys [request-context]}
      (ps/get-providers request-context))))
