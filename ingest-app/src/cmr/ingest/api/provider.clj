(ns cmr.ingest.api.provider
  "Defines the HTTP URL routes for the application."
  (:require [compojure.core :refer :all]
            [cmr.ingest.services.provider-service :as ps]))

(def provider-api-routes
  (context "/providers" []
    ;; create a new provider
    (POST "/" {:keys [request-context body]}
      (ps/create-provider request-context (get body "provider-id")))
    ;; delete a provider
    (DELETE "/:provider-id" {{:keys [provider-id] :as params} :params request-context :request-context}
      (ps/delete-provider request-context provider-id))
    ;; get a list of providers
    (GET "/" {:keys [request-context]}
      (ps/get-providers request-context))))
