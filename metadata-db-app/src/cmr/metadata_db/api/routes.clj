(ns cmr.metadata-db.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.middleware.json :as ring-json]
            [cheshire.core :as json]
            [cmr.common.jobs :as jobs]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api :as api]
            [cmr.common.api.errors :as errors]
            [cmr.common.cache :as cache]
            [cmr.system-trace.http :as http-trace]
            [cmr.metadata-db.services.health-service :as hs]
            [cmr.metadata-db.services.concept-service :as concept-service]
            [cmr.metadata-db.services.jobs :as mdb-jobs]
            [cmr.acl.core :as acl]
            [cmr.metadata-db.api.provider :as provider-api]
            [cmr.metadata-db.api.concepts :as concepts-api]
            [cmr.acl.routes :as common-routes]))

(def admin-api-routes
  "The administrative control routes for metadata db."
  (routes
    ;; delete the entire database
    (POST "/reset" {:keys [request-context params headers]}
      (let [context (acl/add-authentication-to-context request-context params headers)]
        (acl/verify-ingest-management-permission context :update)
        (cache/reset-caches request-context)
        (concept-service/reset context)
        {:status 204}))

    (context "/jobs" []
      ;; pause all jobs
      (POST "/pause" {:keys [request-context params headers]}
        (let [context (acl/add-authentication-to-context request-context params headers)]
          (acl/verify-ingest-management-permission context :update)
          (jobs/pause-jobs (get-in context [:system :scheduler]))
          {:status 204}))

      ;; resume all jobs
      (POST "/resume" {:keys [request-context params headers]}
        (let [context (acl/add-authentication-to-context request-context params headers)]
          (acl/verify-ingest-management-permission context :update)
          (jobs/resume-jobs (get-in context [:system :scheduler]))
          {:status 204}))

      ;; Trigger the old revision concept cleanup
      (POST "/old-revision-concept-cleanup" {:keys [request-context params headers]}
        (let [context (acl/add-authentication-to-context request-context params headers)]
          (acl/verify-ingest-management-permission context :update)
          (mdb-jobs/old-revision-concept-cleanup context)
          {:status 204}))

      (POST "/expired-concept-cleanup" {:keys [request-context params headers]}
        (let [context (acl/add-authentication-to-context request-context params headers)]
          (acl/verify-ingest-management-permission context :update)
          (mdb-jobs/expired-concept-cleanup context)
          {:status 204})))

    (GET "/health" {request-context :request-context :as request}
      (let [pretty? (api/pretty-request? request)
            {:keys [ok? dependencies]} (hs/health request-context)]
        {:status (if ok? 200 503)
         :headers {"Content-Type" "application/json; charset=utf-8"}
         :body (json/generate-string dependencies {:pretty pretty?})}))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- build-routes [system]
  (routes
    (context (:relative-root-url system) []
      concepts-api/concepts-api-routes
      provider-api/provider-api-routes
      common-routes/cache-api-routes
      admin-api-routes)

    (route/not-found "Not Found")))


(defn make-api [system]
  (-> (build-routes system)
      (http-trace/build-request-context-handler system)
      errors/invalid-url-encoding-handler
      errors/exception-handler
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))





