(ns cmr.bootstrap.api.routes
  "Defines the HTTP URL routes for the application."
  (:require
   [cmr.acl.core :as acl]
   [cmr.bootstrap.api.bulk-index :as bulk-index]
   [cmr.bootstrap.api.bulk-migration :as bulk-migration]
   [cmr.bootstrap.api.rebalancing :as rebalancing]
   [cmr.bootstrap.api.virtual-products :as virtual-products]
   [cmr.bootstrap.services.health-service :as hs]
   [cmr.common-app.api.health :as common-health]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common.api.context :as context]
   [cmr.common.api.errors :as errors]
   [cmr.common.jobs :as jobs]
   [cmr.common.log :refer [info]]
   [compojure.core :refer :all]
   [compojure.route :as route]
   [drift.execute :as drift]
   [ring.middleware.json :as ring-json]
   [ring.middleware.keyword-params :as keyword-params]
   [ring.middleware.nested-params :as nested-params]
   [ring.middleware.params :as params]))

(defn- build-routes [system]
  (routes
    (context (:relative-root-url system) []
      ;; for NGAP deployment health check
      (GET "/" {} {:status 200})
      (context "/bulk_migration" []
        (POST "/providers" {:keys [request-context body params]}
          (bulk-migration/migrate-provider request-context body params))
        (POST "/collections" {:keys [request-context body params]}
          (bulk-migration/migrate-collection request-context body params)))
      (context "/bulk_index" []
        (POST "/providers" {:keys [request-context body params]}
          (bulk-index/index-provider request-context body params))
        (POST "/collections" {:keys [request-context body params]}
          (bulk-index/index-collection request-context body params))
        (POST "/after_date_time" {:keys [request-context params]}
          (bulk-index/data-later-than-date-time request-context params))
        (POST "/system_concepts" {:keys [request-context params]}
          (bulk-index/index-system-concepts request-context params))
        (POST "/variables" {:keys [request-context]}
          (bulk-index/index-variables request-context))
        (POST "/variables/:provider-id" [provider-id :as {:keys [request-context]}]
          (bulk-index/index-variables request-context provider-id))
        (POST "/services" {:keys [request-context]}
          (bulk-index/index-services request-context))
        (POST "/services/:provider-id" [provider-id :as {:keys [request-context]}]
          (bulk-index/index-services request-context provider-id))
        (POST "/concepts" {:keys [request-context body params]}
          (bulk-index/index-concepts-by-id request-context body params))
        (DELETE "/concepts" {:keys [request-context body params]}
          (bulk-index/delete-concepts-by-id request-context body params)))
      (context "/rebalancing_collections/:concept-id" [concept-id]
        ;; Start rebalancing
        (POST "/start" {:keys [request-context params]}
          (rebalancing/start-collection request-context concept-id params))
        ;; Get counts of rebalancing data
        (GET "/status" {:keys [request-context]}
          (rebalancing/get-status request-context concept-id))
        ;; Complete reindexing
        (POST "/finalize" {:keys [request-context]}
          (rebalancing/finalize-collection request-context concept-id)))
      (context "/virtual_products" []
        (POST "/" {:keys [request-context params]}
          (virtual-products/bootstrap request-context params)))
      ;; Add routes for accessing caches
      common-routes/cache-api-routes
      ;; db migration route
      (POST "/db-migrate" {:keys [request-context params]}
        (acl/verify-ingest-management-permission request-context :update)
        (let [migrate-args (if-let [version (:version params)]
                             ["migrate" "-version" version]
                             ["migrate"])]
          (info "Running db migration:" migrate-args)
          (drift/run (conj migrate-args "-c" "config.migrate-config/app-migrate-config")))
        {:status 204})
      ;; Add routes for checking health of the application
      (common-health/health-api-routes hs/health))))

(defn make-api [system]
  (-> (build-routes system)
      acl/add-authentication-handler
      errors/invalid-url-encoding-handler
      errors/exception-handler
      common-routes/add-request-id-response-handler
      (context/build-request-context-handler system)
      keyword-params/wrap-keyword-params
      nested-params/wrap-nested-params
      ring-json/wrap-json-body
      common-routes/pretty-print-response-handler
      params/wrap-params))
