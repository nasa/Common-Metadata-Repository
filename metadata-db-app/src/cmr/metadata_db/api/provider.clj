(ns cmr.metadata-db.api.provider
  "Defines the HTTP URL routes for the application."
  (:require [compojure.core :refer :all]
            [cmr.acl.core :as acl]
            [cmr.metadata-db.api.route-helpers :as rh]
            [cmr.metadata-db.services.provider-service :as provider-service]
            [cmr.common.log :refer (debug info warn error)]))

(defn- save-provider
  "Save a provider."
  [context params provider-id]
  (let [saved-provider-id (provider-service/create-provider context provider-id)]
    {:status 201
     :body (rh/to-json saved-provider-id params)
     :headers rh/json-header}))

(defn- delete-provider
  "Delete a provider and all its concepts."
  [context params provider-id]
  (provider-service/delete-provider context provider-id)
  {:status 200})

(defn- get-providers
  "Get a list of provider ids"
  [context params]
  (let [providers (provider-service/get-providers context)]
    {:status 200
     :body (rh/to-json {:providers providers} params)
     :headers rh/json-header}))

(def provider-api-routes
  (context "/providers" []
    ;; create a new provider
    (POST "/" {:keys [request-context params headers body]}
      (let [context (acl/add-authentication-to-context request-context params headers)]
        (acl/verify-ingest-management-permission context :update)
        (save-provider request-context params {:provider-id (get body "provider-id")
                                               :cmr-only (get body "cmr-only")})))
    ;; delete a provider
    (DELETE "/:provider-id" {{:keys [provider-id] :as params} :params
                             request-context :request-context
                             headers :headers}
      (let [context (acl/add-authentication-to-context request-context params headers)]
        (acl/verify-ingest-management-permission context :update)
        (delete-provider request-context params provider-id)))
    ;; get a list of providers
    (GET "/" {:keys [request-context params]}
      (get-providers request-context params))))





