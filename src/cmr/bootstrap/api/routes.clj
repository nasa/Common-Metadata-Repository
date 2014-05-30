(ns cmr.bootstrap.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.middleware.json :as ring-json]
            [cheshire.core :as json]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.system-trace.http :as http-trace]
            [cmr.bootstrap.services.provider :as ps]))

(defn- copy-provider
  "Copy provider data from catalog-rest to metadata db (including collections and granules)"
  [context provider-id-map]
  (let [provider-id (get provider-id-map "provider_id")
        channel (get-in context [:system :provider-channel])]
    (ps/copy-provider channel provider-id)
    {:status 200}))

(defn- build-routes [system]
  (routes
    (context "/bulk_migration" []
      (POST "/providers" {:keys [request-context body]}
        (copy-provider request-context body)))))

(defn make-api [system]
  (-> (build-routes system)
      (http-trace/build-request-context-handler system)
      errors/exception-handler
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))



