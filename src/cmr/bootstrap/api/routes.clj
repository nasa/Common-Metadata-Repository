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
            [cmr.bootstrap.services.copy :as cp]))


(defn- copy-collection
  "Copy collections data from catalog-rest to metadata db (including granules)"
  [context provider-id-collection-map]
  (let [provider-id (get provider-id-collection-map "provider_id")
        collection-id (get provider-id-collection-map "collection_id")
        channel (get-in context [:system :collection-channel])]
    (cp/copy-collection channel provider-id collection-id)
    {:status 202
     :body {:message (str "Processing collection " collection-id "for provider " provider-id)}}))

(defn- copy-provider
  "Copy provider data from catalog-rest to metadata db (including collections and granules)"
  [context provider-id-map]
  (let [provider-id (get provider-id-map "provider_id")
        channel (get-in context [:system :provider-channel])]
    (cp/copy-provider channel provider-id)
    {:status 202 :body {:message (str "Processing provider " provider-id)}}))

(defn- build-routes [system]
  (routes
    (context "/bulk_migration" []
      (POST "/providers" {:keys [request-context body]}
        (copy-provider request-context body))
      (POST "/collections" {:keys [request-context body]}
        (copy-collection request-context body)))))

(defn make-api [system]
  (-> (build-routes system)
      (http-trace/build-request-context-handler system)
      errors/exception-handler
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))



