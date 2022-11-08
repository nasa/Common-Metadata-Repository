(ns cmr.ingest.api.routes
  "Defines the HTTP URL routes for the ingest API."
  (:require
   [cheshire.core :as json]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.health :as common-health]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common.concepts :as concepts]
   [cmr.common.generics :as common-generic]
   [cmr.common.log :refer [info]]
   [cmr.ingest.api.bulk :as bulk]
   [cmr.ingest.api.collections :as collections]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.api.generic-documents :as gen-doc]
   [cmr.ingest.api.granules :as granules]
   [cmr.ingest.api.provider :as provider-api]
   [cmr.ingest.api.services :as services]
   [cmr.ingest.api.subscriptions :as subscriptions]
   [cmr.ingest.services.subscriptions-helper :as subscriptions-helper]
   [cmr.ingest.api.tools :as tools]
   [cmr.ingest.api.translation :as translation-api]
   [cmr.ingest.api.variables :as variables]
   [cmr.ingest.services.ingest-service :as ingest]
   [cmr.ingest.services.job-management :as jm]
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
          "config.ingest-migrate-config/app-migrate-config")))
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
     (POST "/reindex-autocomplete-suggestions"
           {ctx :request-context params :params}
           (acl/verify-ingest-management-permission ctx :update)
           (jobs/trigger-autocomplete-suggestions-reindex ctx)
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
           {:status 200})
     (POST "/trigger-granule-task-cleanup-job"
           {ctx :request-context}
           (acl/verify-ingest-management-permission ctx :update)
           (jobs/trigger-bulk-granule-update-task-table-cleanup ctx)
           {:status 200})
     (POST "/trigger-email-subscription-processing"
           {ctx :request-context params :params}
           (acl/verify-ingest-management-permission ctx :update)
           (subscriptions-helper/trigger-email-subscription-processing ctx params)
           {:status 200})
     (GET  "/email-subscription-processing-job-state"
           {ctx :request-context}
           (let [trigger-state (jm/get-email-subscription-processing-job-state ctx)]
             {:status 200 :body (json/generate-string {:state trigger-state})}))
     (POST "/enable-email-subscription-processing-job"
           {ctx :request-context}
           (acl/verify-ingest-management-permission ctx :update)
           (jm/enable-email-subscription-processing-job ctx)
           {:status 200})
     (POST "/disable-email-subscription-processing-job"
           {ctx :request-context}
           (acl/verify-ingest-management-permission ctx :update)
           (jm/disable-email-subscription-processing-job ctx)
           {:status 200}))))

(def ingest-routes
  (routes
    ;; variable ingest routes with association
    (api-core/set-default-error-format
     :xml
     (context "/collections/:coll-concept-id" [coll-concept-id]
       (context "/:coll-revision-id" [coll-revision-id]
         (context "/variables/:native-id" [native-id]
           (PUT "/"
             request
             (variables/ingest-variable
              nil native-id request coll-concept-id coll-revision-id))))
       (context "/variables/:native-id" [native-id]
         (PUT "/"
           request
           (variables/ingest-variable
            nil native-id request coll-concept-id nil)))))
    ;; Subscriptions
    (api-core/set-default-error-format
     :xml
     (context ["/subscriptions"] []
       (POST "/"
         request
         (subscriptions/create-subscription request))
       (context ["/:native-id" :native-id #".*$"] [native-id]
         (POST "/"
           request
           (subscriptions/create-subscription-with-native-id native-id request))
         (PUT "/"
           request
           (subscriptions/create-or-update-subscription-with-native-id native-id request))
         (DELETE "/"
           request
           (subscriptions/delete-subscription native-id request)))))
    ;; granule bulk update status route
    (api-core/set-default-error-format
     :json
     (context "/granule-bulk-update/status" []
       (POST "/"
         request
         (bulk/update-completed-granule-task-statuses request)
         {:status 200})
       (context "/:task-id" [task-id]
         (GET "/"
           request
           (bulk/get-granule-task-status request task-id)))))
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
           (collections/ingest-collection
            provider-id native-id request))
         (DELETE "/"
           request
           (collections/delete-collection
            provider-id native-id request)))
       ;; Granules
       (context ["/validate/granule/:native-id" :native-id #".*$"] [native-id]
         (POST "/"
           request
           (granules/validate-granule provider-id native-id request)))
       (context ["/granules/:native-id" :native-id #".*$"] [native-id]
         (PUT "/"
           request
           (granules/ingest-granule
            provider-id native-id request))
         (DELETE "/"
           request
           (granules/delete-granule
            provider-id native-id request)))
       ;; Variables
       (context ["/variables/:native-id" :native-id #".*$"] [native-id]
         (PUT "/"
           request
           (variables/ingest-variable
            provider-id native-id request))
         (DELETE "/"
           request
           (variables/delete-variable
            provider-id native-id request)))
       ;; Services
       (context ["/services/:native-id" :native-id #".*$"] [native-id]
         (PUT "/"
           request
           (services/ingest-service
            provider-id native-id request))
         (DELETE "/"
           request
           (services/delete-service
            provider-id native-id request)))
       ;; Tools
       (context ["/tools/:native-id" :native-id #".*$"] [native-id]
         (PUT "/"
           request
           (tools/ingest-tool
            provider-id native-id request))
         (DELETE "/"
           request
           (tools/delete-tool
            provider-id native-id request)))
       ;; Subscriptions
       (context ["/subscriptions"] []
         (POST "/"
           request
           (subscriptions/create-subscription
            provider-id request)))
       (context ["/subscriptions/:native-id" :native-id #".*$"] [native-id]
         (POST "/"
           request
           (subscriptions/create-subscription-with-native-id
            provider-id native-id request))
         (PUT "/"
           request
           (subscriptions/create-or-update-subscription-with-native-id
            provider-id native-id request))
         (DELETE "/"
           request
           (subscriptions/delete-subscription
            provider-id native-id request)))

       ;; Generic documents are by pattern: /providers/{prov_id}/{concept-type}/{native_id}
       (context ["/:concept-type" :concept-type (re-pattern common-generic/plural-generic-concept-types-reg-ex)] [concept-type]
         (context ["/:native-id" :native-id #".*$"] [native-id]
           (POST "/" request (gen-doc/crud-generic-document request :create))
           (GET "/" request (gen-doc/crud-generic-document request :read))
           (PUT "/" request (gen-doc/crud-generic-document request :update))
           (DELETE "/" request (gen-doc/crud-generic-document request :delete))))

       ;; Bulk updates
       (context "/bulk-update/collections" []
         (POST "/"
           request
           (bulk/bulk-update-collections
            provider-id request))
         (GET "/status" ; Gets all tasks for provider
           request
           (bulk/get-provider-tasks :collection provider-id request))
         (GET "/status/:task-id"
           [task-id :as request]
           (bulk/get-collection-task-status provider-id task-id request)))
       (context "/bulk-update/granules" []
         (POST "/"
           request
           (bulk/bulk-update-granules
            provider-id request))
         (GET "/status" ; Gets all tasks for provider
           request
           (bulk/get-provider-tasks :granule provider-id request)))))))

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
