(ns cmr.ingest.api.routes
  "Defines the HTTP URL routes for the ingest API."
  (:require
   [clojure.stacktrace :refer [print-stack-trace]]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.health :as common-health]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common.api.errors :as api-errors]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.ingest.api.ingest :as ingest-api]
   [cmr.ingest.api.multipart :as mp]
   [cmr.ingest.api.provider :as provider-api]
   [cmr.ingest.api.translation :as translation-api]
   [cmr.ingest.services.ingest-service :as ingest]
   [cmr.ingest.services.jobs :as jobs]
   [compojure.core :refer [POST context routes]]
   [drift.execute :as drift]))

(defn build-routes [system]
  (routes
    (context (get-in system [:ingest-public-conf :relative-root-url]) []
      provider-api/provider-api-routes

      ;; Add routes for translating metadata formats
      translation-api/translation-routes

      ;; Add routes to create, update, delete, validate concepts
      ingest-api/ingest-routes

      ;; db migration route
      (POST "/db-migrate"
            {ctx :request-context params :params}
            (acl/verify-ingest-management-permission ctx :update)
            (let [migrate-args (if-let [version (:version params)]
                                 ["migrate" "-version" version]
                                 ["migrate"])]
              (info "Running db migration:" migrate-args)
              (drift/run
               (conj
                migrate-args
                "-c"
                "config.migrate-config/app-migrate-config")))
            {:status 204})

      ;; add routes for managing jobs
      (common-routes/job-api-routes
       (routes
         (POST "/reindex-collection-permitted-groups"
               {ctx :request-context}
               (acl/verify-ingest-management-permission ctx :update)
               (jobs/reindex-collection-permitted-groups ctx)
               {:status 200})
         (POST "/reindex-all-collections"
               {ctx :request-context params :params}
               (acl/verify-ingest-management-permission ctx :update)
               (jobs/reindex-all-collections
                ctx (= "true" (:force_version params)))
               {:status 200})
         (POST "/cleanup-expired-collections"
               {ctx :request-context}
               (acl/verify-ingest-management-permission ctx :update)
               (jobs/cleanup-expired-collections ctx)
               {:status 200})
         (POST "/trigger-full-collection-granule-aggregate-cache-refresh"
               {ctx :request-context}
               (acl/verify-ingest-management-permission ctx :update)
               (jobs/trigger-full-refresh-collection-granule-aggregation-cache
                ctx)
               {:status 200})
         (POST "/trigger-partial-collection-granule-aggregate-cache-refresh"
               {ctx :request-context}
               (acl/verify-ingest-management-permission ctx :update)
               (jobs/trigger-partial-refresh-collection-granule-aggregation-cache
                ctx)
               {:status 200})))

      ;; add routes for accessing caches
      common-routes/cache-api-routes

      ;; add routes for checking health of the application
      (common-health/health-api-routes ingest/health)

      ;; add routes for enabling/disabling writes
      (common-enabled/write-enabled-api-routes
       #(acl/verify-ingest-management-permission % :update)))))
