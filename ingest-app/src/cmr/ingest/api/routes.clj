(ns cmr.ingest.api.routes
  "Defines the HTTP URL routes for the application."
  (:require
   [clojure.stacktrace :refer [print-stack-trace]]
   [cmr.acl.core :as acl]
   [cmr.common-app.api-docs :as api-docs]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.health :as common-health]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common.api.context :as context]
   [cmr.common.api.errors :as api-errors]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.ingest.api.ingest :as ingest-api]
   [cmr.ingest.api.multipart :as mp]
   [cmr.ingest.api.provider :as provider-api]
   [cmr.ingest.api.translation :as translation-api]
   [cmr.ingest.services.ingest-service :as ingest]
   [cmr.ingest.services.jobs :as jobs]
   [compojure.core :refer :all]
   [compojure.route :as route]
   [drift.execute :as drift]
   [ring.middleware.json :as ring-json]
   [ring.middleware.keyword-params :as keyword-params]
   [ring.middleware.nested-params :as nested-params]
   [ring.middleware.params :as params]))

(defn- build-routes [system]
  (routes
    (context (get-in system [:ingest-public-conf :relative-root-url]) []
      provider-api/provider-api-routes

      ;; Add routes for translating metadata formats
      translation-api/translation-routes

      ;; Add routes to create, update, delete, validate concepts
      ingest-api/ingest-routes

      ;; Add routes for API documentation
      (api-docs/docs-routes (get-in system [:ingest-public-conf :protocol])
                            (get-in system [:ingest-public-conf :relative-root-url])
                            "public/ingest_index.html")

      ;; db migration route
      (POST "/db-migrate" {:keys [request-context params]}
        (acl/verify-ingest-management-permission request-context :update)
        (let [migrate-args (if-let [version (:version params)]
                             ["migrate" "-version" version]
                             ["migrate"])]
          (info "Running db migration:" migrate-args)
          (drift/run (conj migrate-args "-c" "config.migrate-config/app-migrate-config")))
        {:status 204})

      ;; add routes for managing jobs
      (common-routes/job-api-routes
       (routes
         (POST "/reindex-collection-permitted-groups" {:keys [headers params request-context]}
           (acl/verify-ingest-management-permission request-context :update)
           (jobs/reindex-collection-permitted-groups request-context)
           {:status 200})
         (POST "/reindex-all-collections" {:keys [headers params request-context]}
           (acl/verify-ingest-management-permission request-context :update)
           (jobs/reindex-all-collections request-context (= "true" (:force_version params)))
           {:status 200})
         (POST "/cleanup-expired-collections" {:keys [headers params request-context]}
           (acl/verify-ingest-management-permission request-context :update)
           (jobs/cleanup-expired-collections request-context)
           {:status 200})
         (POST "/trigger-full-collection-granule-aggregate-cache-refresh"
           {:keys [headers params request-context]}
           (acl/verify-ingest-management-permission request-context :update)
           (jobs/trigger-full-refresh-collection-granule-aggregation-cache request-context)
           {:status 200})
         (POST "/trigger-partial-collection-granule-aggregate-cache-refresh"
           {:keys [headers params request-context]}
           (acl/verify-ingest-management-permission request-context :update)
           (jobs/trigger-partial-refresh-collection-granule-aggregation-cache request-context)
           {:status 200})))

      ;; add routes for accessing caches
      common-routes/cache-api-routes

      ;; add routes for checking health of the application
      (common-health/health-api-routes ingest/health)

      ;; add routes for enabling/disabling writes
      (common-enabled/write-enabled-api-routes
       #(acl/verify-ingest-management-permission % :update)))

    (route/not-found "Not Found")))

(defn default-error-format
  "Determine the format that errors should be returned in based on the default-format
  key set on the ExceptionInfo object passed in as parameter e. Defaults to json if
  the default format has not been set to :xml."
  [_request e]
  (or (mt/format->mime-type (:default-format (ex-data e))) mt/json))

(defn make-api [system]
  (-> (build-routes system)
      acl/add-authentication-handler
      keyword-params/wrap-keyword-params
      nested-params/wrap-nested-params
      api-errors/invalid-url-encoding-handler
      mp/wrap-multipart-params
      (api-errors/exception-handler default-error-format)
      common-routes/add-request-id-response-handler
      (context/build-request-context-handler system)
      common-routes/pretty-print-response-handler
      params/wrap-params))
