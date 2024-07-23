(ns cmr.metadata-db.api.routes
  "Defines the HTTP URL routes for the application."
  (:import [java.io File])
  (:require
   [cmr.acl.core :as acl]
   [cmr.common.memory-db.connection]
   [cmr.common-app.api.health :as common-health]
   [cmr.common-app.api.request-logger :as req-log]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common.api.context :as context]
   [cmr.common.api.errors :as errors]
   [cmr.common.cache :as cache]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.metadata-db.api.concepts :as concepts-api]
   [cmr.metadata-db.api.provider :as provider-api]
   [cmr.metadata-db.api.subscriptions :as subscription-api]
   [cmr.metadata-db.services.concept-service :as concept-service]
   [cmr.metadata-db.services.health-service :as hs]
   [cmr.metadata-db.services.jobs :as mdb-jobs]
   [compojure.core :refer :all]
   [compojure.route :as route]
   [drift.core]
   [drift.execute]
   [ring.middleware.json :as ring-json]
   [ring.middleware.keyword-params :as keyword-params]
   [ring.middleware.nested-params :as nested-params]
   [ring.middleware.params :as params]
   [cmr.metadata-db.services.util :as mdb-util])
  (:require
   ;; These must be required here to make multimethod implementations available.
   ;; XXX This is not a good pattern for large software systems; we need to
   ;;     find a different way to accomplish this goal ... possibly use protocols
   ;;     instead.
   [cmr.metadata-db.data.oracle.concepts.generic-documents]))

(def admin-api-routes
  "The administrative control routes for metadata db."
  (routes
    ;; delete the entire database
    (POST "/reset" {:keys [request-context]}
      (acl/verify-ingest-management-permission request-context :update)
      (cache/reset-caches request-context)
      (concept-service/reset request-context)
      {:status 204})
    (POST "/db-migrate" {:keys [request-context params]}
      (acl/verify-ingest-management-permission request-context :update)

      (let [db (mdb-util/context->db request-context)
            migrate-args (if-let [version (:version params)]
                           ["-c" "config.mdb-migrate-config/app-migrate-config" "-v" version]
                           ["-c" "config.mdb-migrate-config/app-migrate-config"])]
        (info "Running db migration with args:" migrate-args)
        ;; drift looks for migration files within the user.directory, which is /app in service envs.
        ;; Dev dockerfile manually creates /app/cmr-files to store the unzipped cmr jar so that drift
        ;; can find the migration files correctly
        ;; we had to force method change in drift to set the correct path
        (if (not (instance? cmr.common.memory_db.connection.MemoryStore db))
          (try
            ;; trying non-local path to find drift migration files for external oracle db"
            (with-redefs [drift.core/user-directory (fn [] (new File (str (.getProperty (System/getProperties) "user.dir") "/cmr-files")))]
              (drift.execute/run migrate-args))
            (catch Exception e
              (println "caught exception trying to find migration files. We are probably in local env w/ external db. Trying local route to migration files...")
              (with-redefs [drift.core/user-directory (fn [] (new File (str (.getProperty (System/getProperties) "user.dir") "/checkouts/metadata-db-app/src")))]
                (drift.execute/run migrate-args))))))
      {:status 204})))

(def job-api-routes
  (common-routes/job-api-routes
    (routes
      ;; Trigger the old revision concept cleanup
      (POST "/old-revision-concept-cleanup" {:keys [request-context params headers]}
        (acl/verify-ingest-management-permission request-context :update)
        (mdb-jobs/old-revision-concept-cleanup request-context)
        {:status 204})

      (POST "/expired-concept-cleanup" {:keys [request-context params headers]}
        (acl/verify-ingest-management-permission request-context :update)
        (mdb-jobs/expired-concept-cleanup request-context)
        {:status 204}))))

(defn- build-routes [system]
  (routes
    (context (:relative-root-url system) []
      ;; for NGAP deployment health check
      (GET "/" {} {:status 200})

      concepts-api/concepts-api-routes
      provider-api/provider-api-routes
      subscription-api/subscription-api-routes
      common-routes/cache-api-routes
      job-api-routes
      (common-health/health-api-routes hs/health)
      admin-api-routes)

    (route/not-found "Not Found")))

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
      params/wrap-params
      req-log/add-body-hashes
      req-log/log-ring-request))
