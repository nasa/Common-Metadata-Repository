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
   [cmr.ingest.api.bulk :as bulk]
   [cmr.ingest.api.collections :as collections]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.api.granules :as granules]
   [cmr.ingest.api.multipart :as mp]
   [cmr.ingest.api.provider :as provider-api]
   [cmr.ingest.api.services :as services]
   [cmr.ingest.api.translation :as translation-api]
   [cmr.ingest.api.variables :as variables]
   [cmr.ingest.services.ingest-service :as ingest]
   [cmr.ingest.services.jobs :as jobs]
   [compojure.core :refer [DELETE GET POST PUT context routes]]
   [drift.execute :as drift]))

(def db-migration-routes
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
      {:status 204}))

(def job-management-routes
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
           {:status 200}))))

(def ingest-routes
  (routes
    ;; Provider ingest routes
    (api-core/set-default-error-format
      :xml
      (context "/providers/:provider-id" [provider-id]
        ;; Collections
        (context ["/validate/collection/:native-id" :native-id #".*$"] [native-id]
          (POST "/"
                request
                (collections/validate-collection provider-id native-id request)))
        (context ["/collections/:native-id" :native-id #".*$"] [native-id]
          (PUT "/"
               request
               (collections/ingest-collection provider-id native-id request))
          (DELETE "/"
                  request
                  (collections/delete-collection provider-id native-id request)))
        ;; Granules
        (context ["/validate/granule/:native-id" :native-id #".*$"] [native-id]
          (POST "/"
                request
                (granules/validate-granule provider-id native-id request)))
        (context ["/granules/:native-id" :native-id #".*$"] [native-id]
          (PUT "/"
               request
               (granules/ingest-granule provider-id native-id request))
          (DELETE "/"
                  request
                  (granules/delete-granule provider-id native-id request)))
        ;; Variables
        (context ["/variables/:native-id" :native-id #".*$"] [native-id]
          (PUT "/"
               request
               (variables/ingest-variable provider-id native-id request))
          (DELETE "/"
                  request
                  (variables/delete-variable provider-id native-id request)))
        ;; Bulk updates
        (context "/bulk-update/collections" []
          (POST "/"
                request
                (bulk/bulk-update-collections provider-id request))
          (GET "/status" ; Gets all tasks for provider
               request
               (bulk/get-provider-tasks provider-id request))
          (GET "/status/:task-id"
               [task-id :as request]
               (bulk/get-provider-task-status provider-id task-id request)))))
    ;; Services ingest routes
    (api-core/set-default-error-format
      :xml
      (context "/services" []
        (POST "/"
              {:keys [request-context headers body]}
              (services/create-service request-context headers body))
        (PUT "/:service-id"
             [service-id :as {:keys [request-context headers body]}]
             (services/update-service
              request-context headers body service-id))))))

(defn build-routes [system]
  (routes
    (context (get-in system [:public-conf :relative-root-url]) []
      provider-api/provider-api-routes

      ;; Add routes for translating metadata formats
      translation-api/translation-routes

      ;; Add routes to create, update, delete, & validate concepts
      ingest-routes

      ;; db migration route
      db-migration-routes

      ;; add routes for managing jobs
      job-management-routes

      ;; add routes for accessing caches
      common-routes/cache-api-routes

      ;; add routes for checking health of the application
      (common-health/health-api-routes ingest/health)

      ;; add routes for enabling/disabling writes
      (common-enabled/write-enabled-api-routes
       #(acl/verify-ingest-management-permission % :update)))))
