(ns cmr.metadata-db.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.middleware.json :as ring-json]
            [ring.middleware.params :as params]
            [ring.middleware.nested-params :as nested-params]
            [ring.middleware.keyword-params :as keyword-params]
            [cheshire.core :as json]
            [cmr.common.jobs :as jobs]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.common.cache :as cache]
            [cmr.common.api.context :as context]
            [cmr.metadata-db.services.health-service :as hs]
            [cmr.metadata-db.services.concept-service :as concept-service]
            [cmr.metadata-db.services.jobs :as mdb-jobs]
            [cmr.acl.core :as acl]
            [cmr.metadata-db.api.provider :as provider-api]
            [cmr.metadata-db.api.concepts :as concepts-api]
            [cmr.common-app.api.routes :as common-routes]))

(def admin-api-routes
  "The administrative control routes for metadata db."
  (routes
    ;; delete the entire database
    (POST "/reset" {:keys [request-context params headers]}
      (acl/verify-ingest-management-permission request-context :update)
      (cache/reset-caches request-context)
      (concept-service/reset request-context)
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- build-routes [system]
  (routes
    (context (:relative-root-url system) []
      concepts-api/concepts-api-routes
      provider-api/provider-api-routes
      common-routes/cache-api-routes
      job-api-routes
      (common-routes/health-api-routes hs/health)
      admin-api-routes)

    (route/not-found "Not Found")))


(defn make-api [system]
  (-> (build-routes system)
      acl/add-authentication-handler
      (context/build-request-context-handler system)
      errors/invalid-url-encoding-handler
      errors/exception-handler
      keyword-params/wrap-keyword-params
      nested-params/wrap-nested-params
      ring-json/wrap-json-body
      common-routes/pretty-print-response-handler
      params/wrap-params))





