(ns cmr.metadata-db.api.provider
  "Defines the HTTP URL routes for the application."
  (:require [clojure.walk :as walk]
            [compojure.core :refer :all]
            [cmr.acl.core :as acl]
            [cheshire.core :as json]
            [cmr.metadata-db.api.route-helpers :as rh]
            [cmr.metadata-db.services.provider-service :as provider-service]
            [cmr.common.log :refer (debug info warn error)]))

(defn- save-provider
  "Save a provider."
  [context params provider]
  (let [saved-provider-id (provider-service/create-provider context provider)]
    {:status 201
     :body (json/generate-string saved-provider-id)
     :headers rh/json-header}))

(defn- update-provider
  "Update a provider"
  [context params provider]
  (provider-service/update-provider context provider)
  {:status 200
   :body (json/generate-string provider)
   :headers rh/json-header})

(defn- delete-provider
  "Delete a provider and all its concepts."
  [context params provider-id]
  (provider-service/delete-provider context provider-id)
  {:status 204})

(defn- get-providers
  "Get a list of provider ids"
  [context params]
  (let [providers (provider-service/get-providers context)]
    {:status 200
     :body (json/generate-string providers)
     :headers rh/json-header}))

(def provider-api-routes
  (context "/providers" []

    ;; create a new provider
    (POST "/" {:keys [request-context params headers body]}
      (acl/verify-ingest-management-permission request-context :update)
      (let [cmr-only (get body "cmr-only")
            small (get body "small")]
        (save-provider request-context params {:provider-id (get body "provider-id")
                                               :short-name (get body "short-name")
                                               :cmr-only (if (some? cmr-only) cmr-only false)
                                               :small (if (some? small) small false)})))

    ;; update a provider
    (PUT "/:provider-id" {{:keys [provider-id] :as params} :params
                          provider :body
                          request-context :request-context
                          headers :headers}
      (let [provider (walk/keywordize-keys provider)]
        (acl/verify-ingest-management-permission request-context :update)
        (update-provider request-context params provider)))

    ;; delete a provider
    (DELETE "/:provider-id" {{:keys [provider-id] :as params} :params
                             request-context :request-context
                             headers :headers}
      (acl/verify-ingest-management-permission request-context :update)
      (delete-provider request-context params provider-id))

    ;; get a list of providers
    (GET "/" {:keys [request-context params]}
      (get-providers request-context params))))
