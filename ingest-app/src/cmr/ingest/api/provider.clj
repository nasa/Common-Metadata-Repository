(ns cmr.ingest.api.provider
  "Defines the HTTP URL routes for the application."
  (:require [clojure.walk :as walk]
            [compojure.core :refer :all]
            [cmr.common.mime-types :as mt]
            [cmr.acl.core :as acl]
            [cmr.ingest.services.provider-service :as ps]))

(defn- result->response-map
  "Returns the response map of the given result"
  [result]
  (let [{:keys [status body]} result]
    {:status status
     :headers {"Content-Type" (mt/with-utf-8 mt/json)}
     :body body}))

(def provider-api-routes
  (context "/providers" []
    ;; create a new provider
    (POST "/" {:keys [request-context body params headers]}
      (acl/verify-ingest-management-permission request-context :update)
      (result->response-map
        (ps/create-provider request-context {:provider-id (get body "provider-id")
                                             :short-name (get body "short-name")
                                             :cmr-only (get body "cmr-only")
                                             :small (get body "small")})))

    ;; update an existing provider
    (PUT "/:provider-id" {{:keys [provider-id] :as params} :params
                          request-context :request-context
                          provider :body
                          headers :headers}
      (acl/verify-ingest-management-permission request-context :update)
      (result->response-map
        (ps/update-provider request-context (walk/keywordize-keys provider))))

    ;; delete a provider
    (DELETE "/:provider-id" {{:keys [provider-id] :as params} :params
                             request-context :request-context
                             headers :headers}
      (acl/verify-ingest-management-permission request-context :update)
      (result->response-map
        (ps/delete-provider request-context provider-id)))

    ;; get a list of providers
    (GET "/" {:keys [request-context]}
      (result->response-map
        (ps/get-providers-raw request-context)))))
